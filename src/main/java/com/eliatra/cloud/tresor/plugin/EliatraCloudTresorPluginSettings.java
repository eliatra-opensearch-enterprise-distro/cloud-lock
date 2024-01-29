package com.eliatra.cloud.tresor.plugin;


import com.eliatra.cloud.tresor.support.StaticSettings;
import org.opensearch.index.IndexModule;

public class EliatraCloudTresorPluginSettings {

    private EliatraCloudTresorPluginSettings() {

    }

    public static final StaticSettings.Attribute<Boolean> CLOUD_TRESOR_ENABLED =
            StaticSettings.Attribute
                    .define("eliatra.cloud_tresor.enabledeliatra.cloud_tresor.enabled")
                    .withDefault(false)
                    .asBoolean();

    public static final StaticSettings.Attribute<Boolean> INDEX_ENCRYPTION_ENABLED =
            StaticSettings.Attribute
                    .define("index.cloud_tresor_enabled")
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
                    .define("eliatra.cloud_tresor.public_cluster_key")
                    .withDefault((String) null).asString();

    static final StaticSettings.Attribute[] attributes =
            new StaticSettings.Attribute[] {
                    INDEX_ENCRYPTION_ENABLED,
                    NODE_PUBLIC_CLUSTER_KEY,
                    CLOUD_TRESOR_ENABLED,
                    INDEX_STORETYPE_ORIGINAL
            };
}
