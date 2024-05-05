package com.eliatra.cloud.lock.plugin;


import com.eliatra.cloud.lock.support.StaticSettings;
import org.opensearch.index.IndexModule;

public class EliatraCloudLockPluginSettings {

    private EliatraCloudLockPluginSettings() {

    }

    public static final StaticSettings.Attribute<Boolean> CLOUD_LOCK_ENABLED =
            StaticSettings.Attribute
                    .define("eliatra.cloud_lock.enabled")
                    .withDefault(false)
                    .asBoolean();

    public static final StaticSettings.Attribute<Boolean> INDEX_ENCRYPTION_ENABLED =
            StaticSettings.Attribute
                    .define("index.cloud_lock_enabled")
                    .indexScoped()
                    .withDefault(false)
                    .asBoolean();

    public static final StaticSettings.Attribute<String> INDEX_STORETYPE_ORIGINAL =
            StaticSettings.Attribute
                    .define("index.store.type_original")
                    .indexScoped()
                    .withDefault(IndexModule.Type.FS.getSettingsKey())
                    .asString();

    public static final StaticSettings.Attribute<String> NODE_PUBLIC_CLUSTER_KEY =
            StaticSettings.Attribute
                    .define("eliatra.cloud_lock.public_cluster_key")
                    .withDefault((String) null).asString();

    static final StaticSettings.Attribute[] attributes =
            new StaticSettings.Attribute[] {
                    INDEX_ENCRYPTION_ENABLED,
                    NODE_PUBLIC_CLUSTER_KEY,
                    CLOUD_LOCK_ENABLED,
                    INDEX_STORETYPE_ORIGINAL
            };
}
