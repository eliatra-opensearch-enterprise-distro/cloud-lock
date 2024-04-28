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

import org.opensearch.common.blobstore.BlobContainer;
import org.opensearch.common.blobstore.BlobPath;
import org.opensearch.common.blobstore.BlobStore;

import java.io.IOException;

public class EncryptedBlobStore implements BlobStore {

    private final BlobStore delegate;

    public EncryptedBlobStore(BlobStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public BlobContainer blobContainer(BlobPath path) {
        return new EncryptedBlobContainer(delegate.blobContainer(path));
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
