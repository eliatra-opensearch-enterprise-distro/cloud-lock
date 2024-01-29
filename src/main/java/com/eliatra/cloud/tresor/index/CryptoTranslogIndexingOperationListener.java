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

package com.eliatra.cloud.tresor.index;

import com.eliatra.cloud.tresor.crypto.DecryptedSymmetricKey;
import com.eliatra.cloud.tresor.crypto.EncryptedSymmetricKey;
import com.eliatra.cloud.tresor.crypto.SymmetricKek;
import com.eliatra.cloud.tresor.crypto.TemporarySymmetricKey;
import com.eliatra.cloud.tresor.plugin.EliatraCloudTresorPlugin;
import com.eliatra.cloud.tresor.plugin.KeyStore;
import com.eliatra.cloud.tresor.support.BaseDependencies;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.crypto.tink.Aead;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.BytesRef;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.index.IndexService;
import org.opensearch.index.engine.Engine;
import org.opensearch.index.mapper.ParsedDocument;
import org.opensearch.index.mapper.SourceToParse;
import org.opensearch.index.shard.IndexingOperationListener;
import org.opensearch.index.shard.ShardPath;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

public class CryptoTranslogIndexingOperationListener implements IndexingOperationListener {

    protected Logger logger = LogManager.getLogger(getClass());
    private final BaseDependencies baseDependencies;
    private static final String FILENAME = "_encrypted_translog_key";

    private final Cache<ShardId, DecryptedSymmetricKey> keyCache =
            CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .expireAfterAccess(Duration.ofMinutes(60))
                    .build();

    public CryptoTranslogIndexingOperationListener(BaseDependencies baseDependencies) {
        this.baseDependencies = baseDependencies;
    }

    @Override
    public Engine.Index preIndex(ShardId shardId, Engine.Index _operation) {
        //return value is ignored!!
        try {
            return preIndex0(shardId, _operation);
        } catch (Exception e) {
            logger.error("preIndex Error {}",e,e);
            _operation.parsedDoc().docs().clear();
            _operation.parsedDoc().setSource(null, null);
            throw new RuntimeException(e);
        }
    }

    private Engine.Index preIndex0(ShardId shardId, Engine.Index _operation) throws Exception {
        //return value is ignored!!

        final IndexService indexService = baseDependencies.getGuiceDependencies().getIndicesService().indexService(shardId.getIndex());

        if (indexService == null) {
            throw new RuntimeException("indexService must not be null");
        }

        if (KeyStore.INSTANCE.getClusterKey() == null) {
            throw new Exception("Cluster key must not be null here");
        }


        final ShardPath shardPath = indexService.getShard(shardId.id()).shardPath();
        final Path storedFile = shardPath.getDataPath().resolve(FILENAME);

        final DecryptedSymmetricKey key = keyCache.get(shardId, () -> {
            if(Files.exists(storedFile)) {
                final byte[] encryptedKey = Files.readAllBytes(storedFile);
                return KeyStore.INSTANCE.getClusterKey().decryptKey(EncryptedSymmetricKey.fromRawBytes(encryptedKey)).getDecryptedSymmetricKey();
            } else {
                final TemporarySymmetricKey temporarySymmetricKey = KeyStore.INSTANCE.getClusterKey()
                        .newEncryptedSymmetricKey(SymmetricKek.KeyType.AEAD);

                Files.write(storedFile, temporarySymmetricKey.getEncryptedKey(), StandardOpenOption.WRITE, StandardOpenOption.SYNC, StandardOpenOption.DSYNC, StandardOpenOption.CREATE_NEW);
                return temporarySymmetricKey.getDecryptedSymmetricKey();
            }
        });

        logger.trace("preIndex Id: {} of type {} from {}", _operation.id(), _operation.operationType(), _operation.origin());
        //TODO nested/multifield

        if (_operation.origin() == Engine.Operation.Origin.PEER_RECOVERY || _operation.origin() == Engine.Operation.Origin.PRIMARY || _operation.origin() == Engine.Operation.Origin.REPLICA) {
            //peer recovery or primary or replica
            encryptSourceFieldForTranslogAndStoredField(_operation, key, shardId.getIndexName());
            return null;
        }

        if (_operation.origin() == Engine.Operation.Origin.LOCAL_TRANSLOG_RECOVERY || _operation.origin() == Engine.Operation.Origin.LOCAL_RESET) {
            //translog

            final BytesReference decryptedSource = decryptSourceFieldForTranslog(_operation, key, shardId.getIndexName());

            final ParsedDocument decryptedParsedDocument = indexService.mapperService().documentMapper().parse(
                    new SourceToParse(shardId.getIndexName(), _operation.id(), decryptedSource, XContentType.JSON, _operation.routing()));

            _operation.parsedDoc().docs().clear();
            _operation.parsedDoc().docs().addAll(decryptedParsedDocument.docs());

            return null;
        }

        throw new Exception("unreachable code");
    }


    private void encryptSourceFieldForTranslogAndStoredField(Engine.Index operation, DecryptedSymmetricKey key, String indexName) throws Exception {
        final BytesRef source = operation.parsedDoc().source().toBytesRef();
        final byte[] encryptedSource = symmetricCrypt(Arrays.copyOfRange(source.bytes, source.offset, source.offset + source.length), "_source", operation.id(), Cipher.ENCRYPT_MODE, key, indexName);
        operation.parsedDoc().setSource((BytesReference) new BytesArray("{\""+ EliatraCloudTresorPlugin.ENCRYPTED_TL_FIELD_NAME+"\":\""+ Base64.getEncoder().encodeToString(encryptedSource)+"\"}"), (XContentType) operation.parsedDoc().getMediaType());
    }

    private BytesReference decryptSourceFieldForTranslog(Engine.Index operation, DecryptedSymmetricKey key, String indexName) throws Exception {
        final BytesRef source = operation.parsedDoc().source().toBytesRef();
        final Map<String, Object> p = XContentHelper.convertToMap(operation.parsedDoc().getMediaType().xContent(),source.utf8ToString(), false);
        final byte[] decryptedSource = symmetricCrypt(Base64.getDecoder().decode(p.get(EliatraCloudTresorPlugin.ENCRYPTED_TL_FIELD_NAME).toString()), "_source", operation.id(), Cipher.DECRYPT_MODE, key, indexName);
        final BytesReference b = new BytesArray(decryptedSource);
        operation.parsedDoc().setSource(b, (XContentType) operation.parsedDoc().getMediaType());
        return b;
    }


    private byte[] symmetricCrypt(byte[] in, String field, String id, int mode, DecryptedSymmetricKey key, String indexName) throws Exception {

        if (field == null || field.isEmpty()) {
            logger.error("No field set for id {} in index {}", id, indexName);
            throw new RuntimeException("no field set");
        }

        if (id == null || id.isEmpty()) {
            logger.error("No id set for field {} in index {}", field, indexName);
            throw new RuntimeException("no id set");
        }

        if (mode == Cipher.ENCRYPT_MODE) {
            return Base64.getEncoder().encode(key.getPlainKeySetHandle().getPrimitive(Aead.class).encrypt(in, (field + id).getBytes(StandardCharsets.UTF_8)));
        } else {
            return key.getPlainKeySetHandle().getPrimitive(Aead.class).decrypt(Base64.getDecoder().decode(in), (field + id).getBytes(StandardCharsets.UTF_8));
        }
    }
}
