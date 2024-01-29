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

package com.eliatra.cloud.tresor.repo;

import com.eliatra.cloud.tresor.support.BaseDependencies;
import org.opensearch.cluster.metadata.RepositoryMetadata;
import org.opensearch.indices.recovery.RecoverySettings;
import org.opensearch.repositories.Repository;

public class EncryptedRepositoryFactory implements Repository.Factory {

    private final BaseDependencies baseDependencies;
    private final RecoverySettings recoverySettings;

    public EncryptedRepositoryFactory(BaseDependencies baseDependencies, RecoverySettings recoverySettings) {
        this.baseDependencies = baseDependencies;
        this.recoverySettings = recoverySettings;

    }

    @Override
    public Repository create(RepositoryMetadata metadata) throws Exception {
        final String delegateRepo = metadata.settings().get("delegate");

        if(delegateRepo == null || delegateRepo.length()==0) {
            throw new Exception("No delegate set");
        }

        return new EncryptedRepository(baseDependencies.getGuiceDependencies().getRepositoriesService(), delegateRepo, metadata, baseDependencies.getxContentRegistry(), baseDependencies.getClusterService(), recoverySettings);
    }
}
