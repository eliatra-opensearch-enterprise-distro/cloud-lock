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

package com.eliatra.cloud.lock.repo;

import com.eliatra.cloud.lock.crypto.EncryptedSymmetricKey;
import com.eliatra.cloud.lock.crypto.SymmetricKek;
import com.eliatra.cloud.lock.crypto.TemporarySymmetricKey;
import com.eliatra.cloud.lock.plugin.KeyStore;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.StreamingAead;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.blobstore.BlobContainer;
import org.opensearch.common.blobstore.BlobMetadata;
import org.opensearch.common.blobstore.BlobPath;
import org.opensearch.common.blobstore.DeleteResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.SequenceInputStream;
import java.security.GeneralSecurityException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class EncryptedBlobContainer implements BlobContainer {

    protected Logger logger = LogManager.getLogger(getClass());

    private final BlobContainer delegate;

    public EncryptedBlobContainer(BlobContainer delegate) {
        this.delegate = delegate;
    }

    @Override
    public BlobPath path() {
        return delegate.path();
    }

    @Override
    public boolean blobExists(String blobName) throws IOException {
        return delegate.blobExists(blobName);
    }

    @Override
    public InputStream readBlob(String blobName) throws IOException {
        if(KeyStore.INSTANCE.getClusterKey() == null) {
            throw new IOException("Snapshot/Restore: Cluster key not set yet");
        }
        logger.trace("readBlob for {}", blobName);
        InputStream encryptedInputStream = delegate.readBlob(blobName);
        DataInputStream dataInputStream = new DataInputStream(encryptedInputStream);
        int keylen = dataInputStream.readInt();
        byte[] encryptedKey = new byte[keylen];
        dataInputStream.read(encryptedKey);
        try {
            logger.trace("{}/{}",keylen, EncryptedSymmetricKey.fromRawBytes(encryptedKey).getKeyType());
            EncryptedSymmetricKey encryptedSymmetricKey = EncryptedSymmetricKey.fromRawBytes(encryptedKey);
            assert encryptedSymmetricKey.getKeyType() == SymmetricKek.KeyType.STREAMING;

            TemporarySymmetricKey temporarySymmetricKey = KeyStore.INSTANCE.getClusterKey().decryptKey(encryptedSymmetricKey);
            StreamingAead streamingAead = temporarySymmetricKey.getPlainKeySetHandle().getPrimitive(StreamingAead.class);
            return streamingAead.newDecryptingStream(encryptedInputStream, new byte[0]);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public InputStream readBlob(String blobName, long position, long length) throws IOException {
        //TODO its unclear whether long position, long length are based on the encrypted file positions or plain
        //can not find any non-test caller of this method, so it remains unclear
        //assume based on clear text
        /*try {
        final InputStream originalFullInputStream = delegate.readBlob(blobName);
        final InputStream decryptedInputStream = streamingAead.newDecryptingStream(originalFullInputStream, new byte[0]);
        decryptedInputStream.skip(position);
        return Streams.limitStream(decryptedInputStream, length);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }*/
        throw new IOException("Not implemented");
    }

    @Override
    public void writeBlob(String blobName, InputStream inputStream, long blobSize, boolean failIfAlreadyExists) throws IOException {
        logger.trace("writeBlob for {}", blobName);
        if(inputStream instanceof PipedInputStream) {
            delegate.writeBlob(blobName, inputStream, blobSize, failIfAlreadyExists);
        } else {
            try {
                EncInfo encInfo = encryptedKey(blobName);
                StreamingAead streamingAead = encInfo.keysetHandle.getPrimitive(StreamingAead.class);
                int keylen = encInfo.keylen;
                //keylen:: 4byte
                //modbyte: 1 byte
                //key: 164 byte
                //ciphertext


                Vector<InputStream> v = new Vector<>();
                v.add(encInfo.inputStream);
                v.add(encInfo.inputStream2);
                v.add(getCiphertextStream(streamingAead, inputStream));
                Enumeration<InputStream> ins =  v.elements();

                SequenceInputStream sequenceInputStream = new SequenceInputStream(ins);
                delegate.writeBlob(blobName, sequenceInputStream, expectedCiphertextSize(blobSize,32, 4*1024) + keylen, failIfAlreadyExists);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    @Override
    public void writeBlobAtomic(String blobName, InputStream inputStream, long blobSize, boolean failIfAlreadyExists) throws IOException {
        logger.trace("writeBlobAtomic for {}", blobName);
        if(inputStream instanceof PipedInputStream) {
            delegate.writeBlobAtomic(blobName, inputStream, blobSize, failIfAlreadyExists);
        } else {
            try {
                EncInfo encInfo = encryptedKey(blobName);
                StreamingAead streamingAead = encInfo.keysetHandle.getPrimitive(StreamingAead.class);
                int keylen = encInfo.keylen;
                //keylen:: 4byte
                //modbyte: 1 byte
                //key: 164 byte
                //ciphertext

                Vector<InputStream> v = new Vector<>();
                v.add(encInfo.inputStream);
                v.add(encInfo.inputStream2);
                v.add(getCiphertextStream(streamingAead, inputStream));
                Enumeration<InputStream> ins =  v.elements();

                SequenceInputStream sequenceInputStream = new SequenceInputStream(ins);

                delegate.writeBlobAtomic(blobName, sequenceInputStream, expectedCiphertextSize(blobSize,32, 4*1024) + keylen, failIfAlreadyExists);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    private EncInfo encryptedKey(String blobName) throws Exception {
        if(KeyStore.INSTANCE.getClusterKey() == null) {
            throw new Exception("Snapshot/Restore: Cluster key not set yet");
        }
        logger.trace("encryptedKey() for {}", blobName);
        TemporarySymmetricKey temporarySymmetricKey = KeyStore.INSTANCE.getClusterKey().newEncryptedSymmetricKey(SymmetricKek.KeyType.STREAMING);
        assert temporarySymmetricKey.getKeyType() == SymmetricKek.KeyType.STREAMING;
        logger.trace("raw keylen with mode byte {}", temporarySymmetricKey.getEncryptedKey().length);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutput dataOutput = new DataOutputStream(bout);
        dataOutput.writeInt(temporarySymmetricKey.getEncryptedKey().length);
        bout.close();

        //byte[] encryptedKey = CryptoOperations.encryptKey(bout.toByteArray(), EncryptionAtRestPlugin.publicClusterKeys.get(0));
        return new EncInfo(new ByteArrayInputStream(bout.toByteArray()), new ByteArrayInputStream(temporarySymmetricKey.getEncryptedKey()), temporarySymmetricKey.getPlainKeySetHandle(), temporarySymmetricKey.getEncryptedKey().length+bout.toByteArray().length);
    }

    private class EncInfo {
        InputStream inputStream;

        InputStream inputStream2;
        KeysetHandle keysetHandle;
        int keylen;

        public EncInfo(InputStream inputStream,InputStream inputStream2, KeysetHandle keysetHandle, int keylen) {
            this.inputStream = inputStream;
            this.inputStream2 = inputStream2;
            this.keysetHandle = keysetHandle;
            this.keylen = keylen;
        }
    }

    @Override
    public DeleteResult delete() throws IOException {
        return delegate.delete();
    }

    @Override
    public void deleteBlobsIgnoringIfNotExists(List<String> blobNames) throws IOException {
        delegate.deleteBlobsIgnoringIfNotExists(blobNames);
    }

    @Override
    public Map<String, BlobMetadata> listBlobs() throws IOException {
        return delegate.listBlobs();
    }

    @Override
    public Map<String, BlobContainer> children() throws IOException {
        return delegate.children();
    }

    @Override
    public Map<String, BlobMetadata> listBlobsByPrefix(String blobNamePrefix) throws IOException {
        return delegate.listBlobsByPrefix(blobNamePrefix);
    }

    @Override
    public long readBlobPreferredLength() {
        return delegate.readBlobPreferredLength();
    }

    //https://github.com/google/tink/issues/128
    /**
     * Returns an InputStream that provides ciphertext resulting from encryption
     * of 'plaintextStream' with 'associatedData' via 'streamingAead'.
     *
     * NOTE: this method is for demonstration only, and should be adjusted
     *       to specific needs. In particular, the handling of potential
     *       exceptions via a RuntimeException might be not suitable.
     */
    public static PipedInputStream getCiphertextStream(final StreamingAead streamingAead,
                                                  final InputStream plaintextStream)
            throws IOException {

        PipedInputStream ciphertextStream = new PipedInputStream();
        final PipedOutputStream outputStream = new PipedOutputStream(ciphertextStream);
        new Thread(new Runnable() {
            @Override
            public void run(){
                try (OutputStream encryptingStream =
                             streamingAead.newEncryptingStream(outputStream, new byte[0])) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = plaintextStream.read(buffer)) != -1) {
                        encryptingStream.write(buffer, 0, length);
                    }
                    plaintextStream.close();
                } catch (GeneralSecurityException | IOException e) {
                    throw new RuntimeException("Stream encryption failure.", e);
                }
            }
        }
        ).start();
        return ciphertextStream;
    }

    //AesGcmHkdfStreaming.java
    public static long expectedCiphertextSize(long plaintextSize, int keySizeInBytes, int ciphertextSegmentSize) {
        //int ciphertextSegmentSize = 1024*124;
        //int keySizeInBytes 32
        int plaintextSegmentSize = ciphertextSegmentSize - 16;
        long offset = 1 + keySizeInBytes + 7;
        long fullSegments = (plaintextSize + offset) / plaintextSegmentSize;
        long ciphertextSize = fullSegments * ciphertextSegmentSize;
        long lastSegmentSize = (plaintextSize + offset) % plaintextSegmentSize;
        if (lastSegmentSize > 0) {
            ciphertextSize += lastSegmentSize + 16;
        }
        return ciphertextSize;
    }
}
