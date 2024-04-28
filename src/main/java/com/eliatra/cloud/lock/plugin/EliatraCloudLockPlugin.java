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

import com.eliatra.cloud.lock.action.initialize_key.InitializeKeyAction;
import com.eliatra.cloud.lock.action.initialize_key.TransportInitializeKeyAction;
import com.eliatra.cloud.lock.action.update_key.TransportUpdateKeyAction;
import com.eliatra.cloud.lock.action.update_key.UpdateKeyAction;
import com.eliatra.cloud.lock.action.update_key.UpdateKeyRequest;
import com.eliatra.cloud.lock.action.update_key.UpdateKeyResponse;
import com.eliatra.cloud.lock.index.CryptoTranslogIndexingOperationListener;
import com.eliatra.cloud.lock.lucene.encryption.CeffDirectory;
import com.eliatra.cloud.lock.lucene.encryption.CeffMode;
import com.eliatra.cloud.lock.repo.EncryptedRepositoryFactory;
import com.eliatra.cloud.lock.rest.GetEncryptedIndicesApiAction;
import com.eliatra.cloud.lock.rest.InitializeKeyApiAction;
import com.eliatra.cloud.lock.support.BaseDependencies;
import com.eliatra.cloud.lock.support.RSAUtil;
import com.eliatra.cloud.lock.support.StaticSettings;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockFactory;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.admin.cluster.reroute.ClusterRerouteRequest;
import org.opensearch.action.admin.cluster.reroute.ClusterRerouteResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.lifecycle.LifecycleComponent;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.IndexScopedSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsFilter;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.index.IndexModule;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.engine.Engine;
import org.opensearch.index.engine.EngineConfig;
import org.opensearch.index.engine.EngineException;
import org.opensearch.index.engine.EngineFactory;
import org.opensearch.index.engine.InternalEngine;
import org.opensearch.index.shard.ShardPath;
import org.opensearch.index.store.FsDirectoryFactory;
import org.opensearch.indices.recovery.RecoverySettings;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.EnginePlugin;
import org.opensearch.plugins.IndexStorePlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.RepositoryPlugin;
import org.opensearch.repositories.RepositoriesService;
import org.opensearch.repositories.Repository;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.watcher.ResourceWatcherService;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.eliatra.cloud.lock.plugin.EliatraCloudLockPluginSettings.INDEX_STORETYPE_ORIGINAL;
import static org.opensearch.index.store.FsDirectoryFactory.INDEX_LOCK_FACTOR_SETTING;


public class EliatraCloudLockPlugin extends Plugin implements IndexStorePlugin, RepositoryPlugin, ActionPlugin, EnginePlugin {

    public static final String ENCRYPTED_TL_FIELD_NAME = "_encrypted_tl_content";
    public static final FsDirectoryFactory FS_DIRECTORY_FACTORY = new FsDirectoryFactory();

    static {
        try {
            //DeterministicAeadConfig.register();
            AeadConfig.register();
            StreamingAeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    protected Logger logger = LogManager.getLogger(getClass());

    private final boolean enabled;


    private final Settings settings;
    private BaseDependencies baseDependencies;

    public EliatraCloudLockPlugin(final Settings settings, final Path configPath) {
        this.settings = settings;
        enabled = EliatraCloudLockPluginSettings.CLOUD_LOCK_ENABLED.getFrom(settings);

        if(enabled) {
            logger.info("Eliatra Cloud Lock Plugin enabled");
        } else {
            logger.info("Eliatra Cloud Lock Plugin disabled");
        }
    }

    @Override
    public Collection<Object> createComponents(Client client, ClusterService clusterService, ThreadPool threadPool, ResourceWatcherService resourceWatcherService, ScriptService scriptService, NamedXContentRegistry xContentRegistry, Environment environment, NodeEnvironment nodeEnvironment, NamedWriteableRegistry namedWriteableRegistry, IndexNameExpressionResolver indexNameExpressionResolver, Supplier<RepositoriesService> repositoriesServiceSupplier) {
        if (!enabled) {
            return Collections.emptyList();
        }

        final List<Object> components = new ArrayList<Object>();

        final GuiceDependencies guiceDependencies = new GuiceDependencies();
        components.add(guiceDependencies);

        this.baseDependencies = new BaseDependencies(this.settings, null, client, clusterService,
                threadPool, xContentRegistry, environment, nodeEnvironment, indexNameExpressionResolver, guiceDependencies);

        final String _publicClusterKey = EliatraCloudLockPluginSettings.NODE_PUBLIC_CLUSTER_KEY.getFrom(baseDependencies.getSettings());

        if (_publicClusterKey == null || _publicClusterKey.isEmpty()) {
            throw new RuntimeException("No " + EliatraCloudLockPluginSettings.NODE_PUBLIC_CLUSTER_KEY.name() + " set");
        }

        try {
            KeyStore.INSTANCE.setPublicClusterKey(RSAUtil.parsePublicKey(_publicClusterKey));
        } catch (Exception e) {
            logger.error("Error setting public key: {}",e,e);
            throw new RuntimeException(e);
        }

        baseDependencies.getClusterService().addListener(event -> {
            if (!event.localNodeMaster()) {
                return;
            }

            if (event.nodesAdded()) {
                if (KeyStore.INSTANCE.getClusterKey() == null) {
                    logger.trace("MASTER: nodes added but no key yet");
                } else {
                    (baseDependencies.getLocalClient())
                            .execute(UpdateKeyAction.INSTANCE, new UpdateKeyRequest(KeyStore.INSTANCE.getClusterKey()), new ActionListener<UpdateKeyResponse>() {
                                @Override
                                public void onResponse(UpdateKeyResponse updateKeyResponse) {
                                    logger.trace("MASTER: keys updated on all nodes");

                                    baseDependencies.getLocalClient().admin().cluster()
                                            .reroute(new ClusterRerouteRequest().setRetryFailed(true), new ActionListener<ClusterRerouteResponse>() {

                                                @Override
                                                public void onResponse(ClusterRerouteResponse clusterRerouteResponse) {
                                                    logger.trace("reroute by master node {}", clusterRerouteResponse.getExplanations());
                                                }

                                                @Override
                                                public void onFailure(Exception e) {
                                                    logger.error("Reroute error {}",e,e);
                                                }

                                            });
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    logger.error("Reroute error: {}",e,e);
                                }
                            });
                }
            }
        });

        return components;

    }

    @Override
    public Optional<EngineFactory> getEngineFactory(IndexSettings indexSettings) {
        if (!enabled || !EliatraCloudLockPluginSettings.INDEX_ENCRYPTION_ENABLED.getFrom(indexSettings.getSettings())) {
            return Optional.empty();
        }

        return Optional.of(new EngineFactory() {
            @Override
            public Engine newReadWriteEngine(EngineConfig config) {
                return new InternalEngine(config) {
                    @Override
                    public GetResult get(Get get, BiFunction<String, SearcherScope, Searcher> searcherFactory) throws EngineException {
                        return super.get(new Get(false, false, get.id(), get.uid()), searcherFactory);
                    }
                };
            }
        });
    }

    private abstract static class EncryptingDirectoryFactory implements DirectoryFactory {

        abstract Directory createDirectory(FSDirectory delegate, IndexSettings indexSettings, ShardPath shardPath, LockFactory lockFactory) throws Exception;
        @Override
        public Directory newDirectory(IndexSettings indexSettings, ShardPath shardPath) throws IOException {
            if (KeyStore.INSTANCE.getClusterKey() == null) {
                throw new IOException("No cluster key set. This is not an error. We try to handle this situation automatically. Reroute.");
            }

            final IndexMetadata clonedIndexMetadata = IndexMetadata.builder(indexSettings.getIndexMetadata())
                    .settings(Settings.builder()
                            .put(indexSettings.getIndexMetadata().getSettings())
                            .put(IndexModule.INDEX_STORE_TYPE_SETTING.getKey(), INDEX_STORETYPE_ORIGINAL.getFrom(indexSettings.getSettings())))
                    .build();

            final IndexSettings clonedIndexSettings = new IndexSettings(clonedIndexMetadata, indexSettings.getNodeSettings(), indexSettings.getScopedSettings());

            final LockFactory lockFactory = indexSettings.getValue(INDEX_LOCK_FACTOR_SETTING);

            try {
                return createDirectory((FSDirectory) FS_DIRECTORY_FACTORY.newDirectory(clonedIndexSettings, shardPath), clonedIndexSettings, shardPath, lockFactory);
            } catch (Exception e) {
                throw new IOException(e);
            }

        }
    }



    @Override
    public Map<String, DirectoryFactory> getDirectoryFactories() {
        if (!enabled) {
            return Collections.emptyMap();
        }

        Map<String, DirectoryFactory> directories = new HashMap<>();
        directories.put("encrypted", new EncryptingDirectoryFactory() {

            @Override
            Directory createDirectory(FSDirectory delegate, IndexSettings indexSettings, ShardPath shardPath, LockFactory lockFactory) throws Exception {
                return new CeffDirectory(
                        delegate,
                        lockFactory,
                        () -> KeyStore.INSTANCE.getClusterKey(),
                        //64*1024,
                        16*1024,
                        CeffMode.CHACHA20_POLY1305_MODE,
                        false); //TODO fail on plaintext?
            }
        });

        return directories;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return StaticSettings.AttributeSet.of(EliatraCloudLockPluginSettings.attributes).toPlatform();
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        if (!enabled) {
            return Collections.emptyList();
        }
        return List.of(
                new ActionHandler<>(InitializeKeyAction.INSTANCE, TransportInitializeKeyAction.class),
                new ActionHandler<>(UpdateKeyAction.INSTANCE, TransportUpdateKeyAction.class)
        );
    }

    @Override
    public List<RestHandler> getRestHandlers(Settings settings, RestController restController, ClusterSettings clusterSettings, IndexScopedSettings indexScopedSettings, SettingsFilter settingsFilter, IndexNameExpressionResolver indexNameExpressionResolver, Supplier<DiscoveryNodes> nodesInCluster) {
        if (!enabled) {
            return Collections.emptyList();
        }
        return List.of(
                new InitializeKeyApiAction(baseDependencies.getClusterService(), baseDependencies.getThreadPool().getThreadContext()),
                new GetEncryptedIndicesApiAction(baseDependencies.getClusterService(), baseDependencies.getThreadPool().getThreadContext())

        );
    }

    @Override
    public void onIndexModule(IndexModule indexModule) {
        if (!enabled || !EliatraCloudLockPluginSettings.INDEX_ENCRYPTION_ENABLED.getFrom(indexModule.getSettings())) {
            return;
        }

        logger.trace("Index '{}' is encrypted and we register an op listener for it", indexModule.getIndex().getName());

        if (!"encrypted".equals(IndexModule.INDEX_STORE_TYPE_SETTING.get(indexModule.getSettings()))) {
            throw new RuntimeException("store.type must be set to 'encrypted' for index "+indexModule.getIndex().getName());
        }

        //if index is encrypted
        indexModule.addIndexOperationListener(new CryptoTranslogIndexingOperationListener(baseDependencies));
    }

    @Override
    public Map<String, Repository.Factory> getRepositories(Environment env, NamedXContentRegistry namedXContentRegistry, ClusterService clusterService, RecoverySettings recoverySettings) {
        if (!enabled) {
            return Collections.emptyMap();
        }
        try {
            return Collections.singletonMap("encrypted", new EncryptedRepositoryFactory(baseDependencies, recoverySettings));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> getGuiceServiceClasses() {
        if (!enabled) {
            return Collections.emptyList();
        }

        return Arrays.asList(GuiceDependencies.GuiceRedirector.class);
    }
}
