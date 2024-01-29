/*
 * Copyright 2024 by Eliatra - All rights reserved
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed here is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * This software is free of charge for non-commercial and academic use.
 * For commercial use in a production environment you have to obtain a license
 * from https://eliatra.com
 *
 */

package com.eliatra.cloud.tresor.action.initialize_key;

import com.eliatra.cloud.tresor.action.update_key.UpdateKeyAction;
import com.eliatra.cloud.tresor.action.update_key.UpdateKeyRequest;
import com.eliatra.cloud.tresor.action.update_key.UpdateKeyResponse;
import com.eliatra.cloud.tresor.crypto.PlainSymmetricAeadAesKey;
import com.eliatra.cloud.tresor.crypto.SymmetricKek;
import com.eliatra.cloud.tresor.plugin.EliatraCloudTresorPlugin;
import com.eliatra.cloud.tresor.plugin.KeyStore;
import com.eliatra.cloud.tresor.support.DataFilesUtil;
import com.eliatra.cloud.tresor.support.RSAUtil;
import com.google.common.hash.Hashing;
import com.google.crypto.tink.BinaryKeysetReader;
import com.google.crypto.tink.BinaryKeysetWriter;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cryptacular.util.KeyPairUtil;
import org.opensearch.action.admin.cluster.reroute.ClusterRerouteRequest;
import org.opensearch.action.admin.cluster.reroute.ClusterRerouteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.master.TransportMasterNodeAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.block.ClusterBlockException;
import org.opensearch.cluster.block.ClusterBlockLevel;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.env.Environment;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

//manual action triggered via api
public class TransportInitializeKeyAction
extends TransportMasterNodeAction<InitializeKeyRequest, InitializeKeyResponse> {

    protected Logger logger = LogManager.getLogger(getClass());

    private final Client client;

    private final Environment environment;

    @Inject
    public TransportInitializeKeyAction(Client client, final Environment environment,
                                        final ThreadPool threadPool, final ClusterService clusterService, final TransportService transportService,
                                        final ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver) {
        super(
                InitializeKeyAction.NAME,
                transportService,
                clusterService,
                threadPool,
                actionFilters,
                InitializeKeyRequest::new,
                indexNameExpressionResolver
        );

        this.client = client;
        this.environment = environment;
    }

    @Override
    protected String executor() {
        return ThreadPool.Names.SAME;
    }

    @Override
    protected InitializeKeyResponse read(StreamInput in) throws IOException {
        return new InitializeKeyResponse(in);
    }

    @Override
    protected ClusterBlockException checkBlock(InitializeKeyRequest request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected void masterOperation(InitializeKeyRequest request, ClusterState state, ActionListener<InitializeKeyResponse> listener) throws Exception {

        try {

            final PublicKey publicClusterKey = KeyStore.INSTANCE.getPublicClusterKey();

            if(publicClusterKey == null) {
                logger.error("No public key found in the cluster");
                listener.onFailure(new Exception("No public key found in the cluster"));
                return;
            }

            PrivateKey privateClusterKey = null;
            try {
                privateClusterKey = RSAUtil.parsePrivateKey((request.getKey()));
                if(privateClusterKey == null) {
                    logger.error("Can not parse private key");
                    listener.onFailure(new Exception("Can not parse private key"));
                    return;
                }
            } catch (Exception e) {
                logger.error("Can not parse private key: {}",e,e);
                listener.onFailure(new Exception("Can not parse private key", e));
                return;
            }

            if(!KeyPairUtil.isKeyPair(publicClusterKey, privateClusterKey)) {
                logger.error("Wrong key for this cluster");
                listener.onFailure(new Exception("Wrong key for this cluster"));
                return;
            }


            final Tuple<KeysetHandle, byte[]> plaintTextClusterKey = generateOrLoadSymmetricKey(new KeyPair(publicClusterKey, privateClusterKey), "_encrypted_cluster_key");
            final SymmetricKek symmetricKek = new SymmetricKek(new PlainSymmetricAeadAesKey(plaintTextClusterKey.v1()), plaintTextClusterKey.v2());

            client.execute(UpdateKeyAction.INSTANCE, new UpdateKeyRequest(symmetricKek), new ActionListener<UpdateKeyResponse>() {

                        @Override
                        public void onResponse(UpdateKeyResponse updateKeyResponse) {
                            client.admin().cluster()
                                    .reroute(new ClusterRerouteRequest().setRetryFailed(true), new ActionListener<ClusterRerouteResponse>() {

                                        @Override
                                        public void onResponse(ClusterRerouteResponse clusterRerouteResponse) {

                                            listener.onResponse(
                                                    new InitializeKeyResponse(updateKeyResponse)
                                            );
                                        }

                                        @Override
                                        public void onFailure(Exception e) {
                                            listener.onFailure(e);
                                        }

                                    });
                        }

                        @Override
                        public void onFailure(Exception e) {
                            listener.onFailure(e);
                        }

                    });

        } catch (Exception e) {
            logger.error("Initialization exception: {}",e,e);
            throw new RuntimeException(e);
        }
    }

    private Tuple<KeysetHandle, byte[]> generateOrLoadSymmetricKey(KeyPair keyPair, String filename) throws Exception {
        Path storeDir = DataFilesUtil.findLocation(environment, filename);
        Path storedFile = storeDir.resolve(filename);

        if(Files.exists(storedFile)) {
            logger.trace("Read cluster key (on master) from {} ({})", storedFile, hashOfFile(storedFile));
            byte[] encryptedKey = Files.readAllBytes(storedFile);
            return new Tuple<>(CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(RSAUtil.decryptKey(encryptedKey, keyPair.getPrivate()))),encryptedKey);
        } else {
            KeysetHandle keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            CleartextKeysetHandle.write(keysetHandle, BinaryKeysetWriter.withOutputStream(bout));
            byte[] rsaEncryptedKek = RSAUtil.encryptKey(bout.toByteArray(), keyPair.getPublic());
            Files.write(storedFile, rsaEncryptedKek, StandardOpenOption.WRITE, StandardOpenOption.SYNC, StandardOpenOption.DSYNC, StandardOpenOption.CREATE_NEW);
            logger.trace("Store new cluster key (on master) under {} ({})", storedFile, hashOfFile(storedFile));
            return new Tuple<>(keysetHandle,rsaEncryptedKek) ;
        }
    }

    private String hashOfFile(Path file) throws IOException {
        return Hashing.sha256().hashBytes(Files.readAllBytes(file)).toString();
    }

}
