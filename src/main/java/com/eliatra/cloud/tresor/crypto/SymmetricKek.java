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

package com.eliatra.cloud.tresor.crypto;

import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public class SymmetricKek implements Writeable {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public enum KeyType {
        PLAIN_AES ((byte)-12),
      AEAD((byte)-14),
      STREAMING((byte)-27);

      private final byte modeByte;

        KeyType(byte modeByte) {
            this.modeByte = modeByte;
        }

        public byte getModeByte() {
            return modeByte;
        }

        public static KeyType getByModeByte(byte modeByte) {
            return Arrays.stream(KeyType.values()).filter(k->k.getModeByte() == modeByte).findFirst().orElseThrow(()->new IllegalArgumentException("No such mode "+modeByte));
        }
    }

    private final PlainSymmetricAeadAesKey plainSymmetricKey;

    private final byte[] rsaEncryptedBytes;

    public SymmetricKek(StreamInput in) throws IOException {
        try {
            this.plainSymmetricKey = new PlainSymmetricAeadAesKey(in.readByteArray());
            this.rsaEncryptedBytes = in.readByteArray();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public SymmetricKek(PlainSymmetricAeadAesKey kek, byte[] rsaEncryptedBytes) {
        this.plainSymmetricKey = kek;
        this.rsaEncryptedBytes = rsaEncryptedBytes;
    }

    public TemporarySymmetricKey newEncryptedSymmetricKey(KeyType keyType) throws GeneralSecurityException, IOException {
        KeysetHandle keysetHandle;

        switch (keyType) {
            case AEAD: keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM")); break;
            case STREAMING: keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_4KB")); break;
            case PLAIN_AES: keysetHandle = null; break;
            default: throw new GeneralSecurityException("Invalid key type "+keyType);
        }

        if(keysetHandle == null) {
            byte[] plain = new byte[32];
            SECURE_RANDOM.nextBytes(plain);
            return new TemporarySymmetricKey(encrypt0(plain), plain, keyType);
        } else {
            return new TemporarySymmetricKey(encrypt0(keysetHandle), keysetHandle, keyType);
        }
    }

    private byte[] encrypt0(byte[] plain) throws GeneralSecurityException, IOException {
        return plainSymmetricKey.encryptDataWithAes(plain);
    }

    private byte[] encrypt0(KeysetHandle keysetHandle) throws GeneralSecurityException, IOException {
        return plainSymmetricKey.encryptKeysetHandleWithAes(keysetHandle);
    }

    private byte[] decrypt0(byte[] ciphertext) throws GeneralSecurityException, IOException {
        return plainSymmetricKey.decryptDataWithAes(ciphertext);
    }

    private KeysetHandle decrypt0(EncryptedSymmetricKey encryptedSymmetricKey) throws GeneralSecurityException, IOException {
        return plainSymmetricKey.decryptKeyWithAes(encryptedSymmetricKey);
    }

    public TemporarySymmetricKey decryptKey(EncryptedSymmetricKey encryptedSymmetricKey) throws GeneralSecurityException, IOException {
        if(encryptedSymmetricKey.getKeyType() == KeyType.PLAIN_AES) {
            return new TemporarySymmetricKey(encryptedSymmetricKey.getEncryptedKey(), decrypt0(encryptedSymmetricKey.getEncryptedKey()), encryptedSymmetricKey.getKeyType());
        }
        return new TemporarySymmetricKey(encryptedSymmetricKey.getEncryptedKey(), decrypt0(encryptedSymmetricKey), encryptedSymmetricKey.getKeyType());
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeByteArray(plainSymmetricKey.getPlainSymmetricAeadAesKey());
        out.writeByteArray(rsaEncryptedBytes);
    }

    public byte[] getRsaEncryptedBytes() {
        return rsaEncryptedBytes;
    }
}
