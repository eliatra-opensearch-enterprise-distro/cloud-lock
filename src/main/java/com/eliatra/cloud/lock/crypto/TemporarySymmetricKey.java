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

package com.eliatra.cloud.lock.crypto;

import com.eliatra.cloud.lock.lucene.encryption.CeffUtils;
import com.google.crypto.tink.BinaryKeysetWriter;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.KeysetHandle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;

public final class TemporarySymmetricKey {

    private final byte[] encryptedKey;

    private final byte[] plainKey;
    private final KeysetHandle plainKeysetHandle;

    private final SymmetricKek.KeyType keyType;

    public TemporarySymmetricKey(byte[] encryptedKey, KeysetHandle plainKeysetHandle, SymmetricKek.KeyType keyType) {
        this.encryptedKey = encryptedKey;
        this.plainKeysetHandle = plainKeysetHandle;
        this.keyType = keyType;
        this.plainKey = null;
    }

    public TemporarySymmetricKey(byte[] encryptedKey, byte[] plainKey, SymmetricKek.KeyType keyType) {
        this.encryptedKey = encryptedKey;
        this.plainKeysetHandle = null;
        this.keyType = keyType;
        this.plainKey = plainKey.clone();
    }

    public byte[] getEncryptedKey() {
        return CeffUtils.concatArrays(new byte[]{keyType.getModeByte()}, encryptedKey);
    }

    public String getEncryptedKeyBase64Encoded() {
        return Base64.getEncoder().encodeToString(getEncryptedKey());
    }

    public KeysetHandle getPlainKeySetHandle() throws GeneralSecurityException {
        return plainKeysetHandle;
    }

    public byte[] getPlainKeySetHandleAsBytes() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        CleartextKeysetHandle.write(plainKeysetHandle, BinaryKeysetWriter.withOutputStream(bout));
        return CeffUtils.concatArrays(new byte[]{keyType.getModeByte()},bout.toByteArray());
    }

    public byte[] getPlainKey() {
        return plainKey;
    }

    public SymmetricKek.KeyType getKeyType() {
        return keyType;
    }

    public DecryptedSymmetricKey getDecryptedSymmetricKey() {
        return new DecryptedSymmetricKey(plainKeysetHandle);
    }
}
