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

import com.eliatra.cloud.lock.plugin.GuiceDependencies;
import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.threadpool.ThreadPool;

public class BaseDependencies {

    private final Settings settings;
    private final StaticSettings staticSettings;
    private final Client localClient;
    private final ClusterService clusterService;
    private final ThreadPool threadPool;
    private final NamedXContentRegistry xContentRegistry;
    private final Environment environment;
    private final IndexNameExpressionResolver indexNameExpressionResolver;
    private final NodeEnvironment nodeEnvironment;
    private final GuiceDependencies guiceDependencies;

    public BaseDependencies(Settings settings, StaticSettings staticSettings, Client localClient, ClusterService clusterService,
                            ThreadPool threadPool, NamedXContentRegistry xContentRegistry,
                            Environment environment,
                            NodeEnvironment nodeEnvironment,
                            IndexNameExpressionResolver indexNameExpressionResolver,
                            GuiceDependencies guiceDependencies
                            ) {
        super();
        this.settings = settings;
        this.staticSettings = staticSettings;
        this.localClient = localClient;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.xContentRegistry = xContentRegistry;
        this.environment = environment;
        this.nodeEnvironment = nodeEnvironment;
        this.indexNameExpressionResolver = indexNameExpressionResolver;
        this.guiceDependencies = guiceDependencies;
    }

    public Settings getSettings() {
        return settings;
    }

    public Client getLocalClient() {
        return localClient;
    }

    public ClusterService getClusterService() {
        return clusterService;
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }


    public NamedXContentRegistry getxContentRegistry() {
        return xContentRegistry;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public IndexNameExpressionResolver getIndexNameExpressionResolver() {
        return indexNameExpressionResolver;
    }

    public NodeEnvironment getNodeEnvironment() {
        return nodeEnvironment;
    }



    public StaticSettings getStaticSettings() {
        return staticSettings;
    }

    public GuiceDependencies getGuiceDependencies() {
        return guiceDependencies;
    }

}