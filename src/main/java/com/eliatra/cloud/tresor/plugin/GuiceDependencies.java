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


package com.eliatra.cloud.tresor.plugin;

import org.opensearch.common.inject.Inject;
import org.opensearch.common.lifecycle.Lifecycle;
import org.opensearch.common.lifecycle.LifecycleComponent;
import org.opensearch.common.lifecycle.LifecycleListener;
import org.opensearch.indices.IndicesService;
import org.opensearch.repositories.RepositoriesService;

/**
 * Very hackish way to get hold to Guice components from Non-Guice components.
 */
public class GuiceDependencies {
    private RepositoriesService repositoriesService;
    private IndicesService indicesService;

    GuiceDependencies() {
        
    }
    
    public RepositoriesService getRepositoriesService() {
        return repositoriesService;
    }

    public IndicesService getIndicesService() {
        return indicesService;
    }
    
    public static class GuiceRedirector implements LifecycleComponent {

        @Inject
        public GuiceRedirector(GuiceDependencies baseDependencies, RepositoriesService repositoriesService, IndicesService indicesService) {
            baseDependencies.repositoriesService = repositoriesService;
            baseDependencies.indicesService = indicesService;
        }

        @Override
        public void close() {
        }

        @Override
        public Lifecycle.State lifecycleState() {
            return null;
        }

        @Override
        public void addLifecycleListener(LifecycleListener listener) {
        }

        @Override
        public void removeLifecycleListener(LifecycleListener listener) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

    }

}
