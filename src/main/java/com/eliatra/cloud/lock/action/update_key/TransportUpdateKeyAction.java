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

package com.eliatra.cloud.lock.action.update_key;

import com.eliatra.cloud.lock.plugin.KeyStore;
import com.eliatra.cloud.lock.support.DataFilesUtil;
import com.google.common.hash.Hashing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.env.Environment;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class TransportUpdateKeyAction
extends
TransportNodesAction<UpdateKeyRequest, UpdateKeyResponse, TransportUpdateKeyAction.NodeKeyUpdateRequest, UpdateKeyNodeResponse> {

    protected Logger logger = LogManager.getLogger(getClass());
    private final Environment env;
    
    @Inject
    public TransportUpdateKeyAction(Client client, final Environment env,
                                    final ThreadPool threadPool, final ClusterService clusterService, final TransportService transportService,
                                    final ActionFilters actionFilters) {
        super(UpdateKeyAction.NAME, threadPool, clusterService, transportService, actionFilters,
                UpdateKeyRequest::new, NodeKeyUpdateRequest::new,
                ThreadPool.Names.MANAGEMENT, UpdateKeyNodeResponse.class);

        this.env = env;
    }

    public static class NodeKeyUpdateRequest extends BaseNodeRequest {

        UpdateKeyRequest request;
        
        public NodeKeyUpdateRequest(StreamInput in) throws IOException {
            super(in);
            request = new UpdateKeyRequest(in);
        }

        public NodeKeyUpdateRequest(final UpdateKeyRequest request) {
            this.request = request;
        }

        @Override
        public void writeTo(final StreamOutput out) throws IOException {
            super.writeTo(out);
            request.writeTo(out);
        }
    }

    @Override
    protected UpdateKeyNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new UpdateKeyNodeResponse(in);
    }
    
    @Override
    protected UpdateKeyResponse newResponse(UpdateKeyRequest request, List<UpdateKeyNodeResponse> responses,
                                            List<FailedNodeException> failures) {
        return new UpdateKeyResponse(this.clusterService.getClusterName(), responses, failures);

    }

    @Override
    protected UpdateKeyNodeResponse nodeOperation(final NodeKeyUpdateRequest request) {
        try {
            DiscoveryNode masterNode = clusterService.state().nodes().getMasterNode();
            DiscoveryNode localNode = clusterService.localNode();

            localNode.equals(masterNode);

            boolean set = KeyStore.INSTANCE.setClusterKey(request.request.getKey());
            boolean saved = saveSymmetricKey(request.request.getKey().getRsaEncryptedBytes(),"_encrypted_cluster_key");
            logger.trace("Update cluster key: master {}, set {}, saved {}", localNode.equals(masterNode), set, saved);
            return new UpdateKeyNodeResponse(this.clusterService.localNode(), localNode.equals(masterNode), set, saved);
        } catch (Exception e) {
            logger.error("Update keys failed: {}",e,e);
            throw new RuntimeException(e);
        }
    }

    private boolean saveSymmetricKey(byte[] bytes, String filename) throws Exception {
        Path storeDir = DataFilesUtil.findLocation(env, filename);
        Path storedFile = storeDir.resolve(filename);

        if(!Files.exists(storedFile)) {
            Files.write(storedFile, bytes, StandardOpenOption.WRITE, StandardOpenOption.SYNC, StandardOpenOption.DSYNC, StandardOpenOption.CREATE_NEW);
            logger.trace("Store sent cluster key under {} ({})", storedFile, hashOfFile(storedFile));
            return true;
        } else {
            logger.trace("Key already exists under {} ({})", storedFile, hashOfFile(storedFile));
            return false;
        }
    }

    private String hashOfFile(Path file) throws IOException {
        return Hashing.sha256().hashBytes(Files.readAllBytes(file)).toString();
    }

    @Override
    protected NodeKeyUpdateRequest newNodeRequest(UpdateKeyRequest request) {
        return new NodeKeyUpdateRequest(request);
    }
}
