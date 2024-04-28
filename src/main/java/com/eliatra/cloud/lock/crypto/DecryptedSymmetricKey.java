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

import com.google.crypto.tink.KeysetHandle;

public class DecryptedSymmetricKey {

    private final KeysetHandle plainKeysetHandle;

    public DecryptedSymmetricKey(KeysetHandle plainKeysetHandle) {
        this.plainKeysetHandle = plainKeysetHandle;
    }

    public KeysetHandle getPlainKeySetHandle() {
        return plainKeysetHandle;
    }
}
