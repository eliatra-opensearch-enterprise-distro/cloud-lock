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

import org.opensearch.cluster.metadata.RepositoryMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.blobstore.BlobPath;
import org.opensearch.common.blobstore.BlobStore;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.indices.recovery.RecoverySettings;
import org.opensearch.repositories.RepositoriesService;
import org.opensearch.repositories.blobstore.BlobStoreRepository;

public class EncryptedRepository extends BlobStoreRepository {

    private final RepositoriesService repositoriesService;
    private final String delegateRepo;
    private BlobStoreRepository delegate;

    private void lazyDelegateInit() {
        if(delegate == null) {
            delegate = (BlobStoreRepository) repositoriesService.repository(delegateRepo);

            if (delegate == null) {
                throw new RuntimeException("No repo for delegate " + delegateRepo + " found");
            }
        }
    }

    EncryptedRepository(RepositoriesService repositoriesService, String delegateRepo, RepositoryMetadata metadata, NamedXContentRegistry namedXContentRegistry, ClusterService clusterService, RecoverySettings recoverySettings) {
        super(metadata, namedXContentRegistry, clusterService, recoverySettings);
        this.repositoriesService = repositoriesService;
        this.delegateRepo = delegateRepo;
    }

    @Override
    protected BlobStore createBlobStore() throws Exception {
        lazyDelegateInit();
        return new EncryptedBlobStore(delegate.blobStore());
    }

    @Override
    public BlobPath basePath() {
        lazyDelegateInit();
        return delegate.basePath();
    }
}
