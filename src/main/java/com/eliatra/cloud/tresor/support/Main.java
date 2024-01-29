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

package com.eliatra.cloud.tresor.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public class Main {

    public static void main(String[] args) throws Exception {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");

        kpg.initialize(2048);
        KeyPair keypair = kpg.generateKeyPair();
        PublicKey publickey = keypair.getPublic();
        PrivateKey privateKey = keypair.getPrivate();

        System.out.println("Public Key:");
        System.out.println(Base64.getEncoder().encodeToString(publickey.getEncoded()));
        System.out.println();
        System.out.println("Private Key:");
        System.out.println(Base64.getEncoder().encodeToString(privateKey.getEncoded()));


    }

}
