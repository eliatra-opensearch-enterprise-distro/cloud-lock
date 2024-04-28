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

package com.eliatra.cloud.lock.support;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSAUtil {

    public static PublicKey parsePublicKey(String publicKey) throws Exception {
        byte[] key = Base64.getDecoder().decode(publicKey);
        X509EncodedKeySpec spec =
                new X509EncodedKeySpec(key);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    public static PrivateKey parsePrivateKey(byte[] privateKey) throws Exception {
        return parsePrivateKey0(Base64.getDecoder().decode(privateKey));
    }

    private static PrivateKey parsePrivateKey0(byte[] privateKeyRaw) throws Exception {
        PKCS8EncodedKeySpec spec =
                new PKCS8EncodedKeySpec(privateKeyRaw);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        return kf.generatePrivate(spec);
    }

    public static byte[] decryptKey(byte[] encrypted_keys, PrivateKey pk) throws Exception {
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.DECRYPT_MODE, pk);
        return encryptCipher.doFinal(encrypted_keys);
    }

    public static byte[] encryptKey(byte[] k, PublicKey publicKey) throws Exception {
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return encryptCipher.doFinal(k);
    }
}
