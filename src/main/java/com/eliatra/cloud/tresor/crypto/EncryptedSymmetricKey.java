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

import java.util.Arrays;
import java.util.Base64;

public class EncryptedSymmetricKey {

    private final byte[] encryptedKey;

    private final SymmetricKek.KeyType keyType;

    EncryptedSymmetricKey(byte[] encryptedKey, SymmetricKek.KeyType keyType) {
        this.encryptedKey = encryptedKey;
        this.keyType = keyType;
    }

    public static EncryptedSymmetricKey fromRawBytes(byte[] encryptedKey) {
        return new EncryptedSymmetricKey(Arrays.copyOfRange(encryptedKey,1,encryptedKey.length), SymmetricKek.KeyType.getByModeByte(encryptedKey[0]));
    }

    public static EncryptedSymmetricKey fromRawBase64EncodedBytes(String encryptedKey) {
        return fromRawBytes(Base64.getDecoder().decode(encryptedKey));
    }

    public SymmetricKek.KeyType getKeyType() {
        return keyType;
    }

    public byte[] getEncryptedKey() {
        return encryptedKey.clone();
    }

}
