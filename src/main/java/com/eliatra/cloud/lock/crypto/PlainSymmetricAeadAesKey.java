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

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.BinaryKeysetReader;
import com.google.crypto.tink.BinaryKeysetWriter;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.KeysetHandle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

//this is the key which encypts/decrypts the KEK
public final class PlainSymmetricAeadAesKey {

    //TODO checks clones in keys and add destroyable

    private final Aead aead;
    private final byte[] plainSymmetricAeadAesKey;

    private final KeysetHandle keysetHandle;

    PlainSymmetricAeadAesKey(byte[] plainSymmetricAeadAesKey) throws GeneralSecurityException, IOException {
        this.plainSymmetricAeadAesKey = plainSymmetricAeadAesKey.clone();
        this.keysetHandle = CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(this.plainSymmetricAeadAesKey));
        aead = this.keysetHandle.getPrimitive(Aead.class);
    }

    public PlainSymmetricAeadAesKey(KeysetHandle keysetHandle) throws GeneralSecurityException, IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        CleartextKeysetHandle.write(keysetHandle, BinaryKeysetWriter.withOutputStream(bout));
        this.plainSymmetricAeadAesKey = bout.toByteArray();
        this.keysetHandle = keysetHandle;
        aead = this.keysetHandle.getPrimitive(Aead.class);
    }

    public byte[] encryptKeysetHandleWithAes(KeysetHandle keysetHandle) throws IOException, GeneralSecurityException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        CleartextKeysetHandle.write(keysetHandle, BinaryKeysetWriter.withOutputStream(bout));
        return aead.encrypt(bout.toByteArray(), new byte[0]);
    }

    public KeysetHandle decryptKeyWithAes(EncryptedSymmetricKey encryptedSymmetricKey) throws GeneralSecurityException, IOException {
        byte [] b = aead.decrypt(encryptedSymmetricKey.getEncryptedKey(), new byte[0]);
        return CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(b));
    }

    public byte[] encryptDataWithAes(byte[] plainTextData) throws GeneralSecurityException {
        return aead.encrypt(plainTextData, new byte[0]);
    }

    public byte[] decryptDataWithAes(byte[] cipherText) throws GeneralSecurityException {
        return aead.decrypt(cipherText, new byte[0]);
    }

    byte[] getPlainSymmetricAeadAesKey() {
        return plainSymmetricAeadAesKey.clone();
    }
}
