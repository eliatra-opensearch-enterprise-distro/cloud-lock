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

package com.eliatra.cloud.lock.plugin;

import com.eliatra.cloud.lock.crypto.SymmetricKek;
import org.apache.lucene.util.SetOnce;

import java.security.PublicKey;

public class KeyStore {
    public static final KeyStore INSTANCE = new KeyStore();

    private KeyStore() {}

    private final SetOnce<SymmetricKek> clusterKey = new SetOnce<>();

    private final SetOnce<PublicKey> publicClusterKey = new SetOnce<>();

    public boolean setClusterKey(SymmetricKek key) {
        return clusterKey.trySet(key);
    }

    public PublicKey getPublicClusterKey() {
        return publicClusterKey.get();
    }

    public SymmetricKek getClusterKey() {
        return clusterKey.get();
    }

    public void setPublicClusterKey(PublicKey publicKey) {
        publicClusterKey.trySet(publicKey);
    }
}
