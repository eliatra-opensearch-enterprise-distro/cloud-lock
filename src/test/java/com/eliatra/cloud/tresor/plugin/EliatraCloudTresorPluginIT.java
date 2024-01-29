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

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import org.apache.http.util.EntityUtils;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.common.settings.Settings;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchIntegTestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class EliatraCloudTresorPluginIT extends OpenSearchIntegTestCase {

    private static final String PUBLIC_TEST_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtKEA5avMCUDPB3dzVtygQ7dNz8g/sroop6w94MBAVYgm2qNJVMdcdEA1tLz09rbpTwnIQs6uLSALtovNwxrnYOxHeYQy85qlvUBbUW8XWpZoaEFqpnWtI66yNtZBdIuu4+KlvQggo8rJ79HDM6GT8SB3lA9KFwjazwlkVFZT/KoI51Ut3CheXa1ym/XUwsSTCPWfyOcOp+oaDV0peMRz8zJwQUyhSDaEH1hfMsZMgjI9Co95Wiwfonzyc/o/5M9pEfjw3e5RydxJVFxGWYvWf/FG2h5kee1O/Hty/DVOLIJvxAQJyQ5yeXKvLXrHjoxBF7aJ/71XcO6/2Db7JBfrZwIDAQAB";
    private static final String PRIVATE_TEST_KEY = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC0oQDlq8wJQM8Hd3NW3KBDt03PyD+yuiinrD3gwEBViCbao0lUx1x0QDW0vPT2tulPCchCzq4tIAu2i83DGudg7Ed5hDLzmqW9QFtRbxdalmhoQWqmda0jrrI21kF0i67j4qW9CCCjysnv0cMzoZPxIHeUD0oXCNrPCWRUVlP8qgjnVS3cKF5drXKb9dTCxJMI9Z/I5w6n6hoNXSl4xHPzMnBBTKFINoQfWF8yxkyCMj0Kj3laLB+ifPJz+j/kz2kR+PDd7lHJ3ElUXEZZi9Z/8UbaHmR57U78e3L8NU4sgm/EBAnJDnJ5cq8teseOjEEXton/vVdw7r/YNvskF+tnAgMBAAECggEADVsyY8k2RyDhrh2pC604tIkjWc/m1eJqCyvzT2En4Ks2pEgarggnz/jHf9vRCUUxL0T75+S8gF20QAVKa7jbjxEpn9Skp9qxkrLljbn3Mh2ZDsx3hGODL/ZE+0UQhfumLPefZcDqGPJvyCnsky7Jb7UE4o/W3Kks9M4u+wV/JFmWhkOrT41MFDOEcscVSHQvZxDiCutqkczpbH5nPHHQpCblLprXm9Gym56ZbVJmQC+KJpQom3BjTISPm9cQO9JC2FU36XTwGur6kRDr7UM1lhqvtoFwL7bDniRZT69mr5Qit5g/uEQx2Jzli0ZwcCi4g+JCWA8qzh0pHofjK3hdQQKBgQDN/kbh0YE8PFTc1hmr151FHFXu1c+o2CWAiRK3B7cMzIZS1lU1LXtp9fsjyV1DOZGb/409r1XEsMkyPWwyY9tm6wm8vpiBlK2CqJBGoQl8aS0vmYN2EOkROVDim1KdHYpmEFcTOsAsHlrrZdrUlhBWOMtjrSFICWbpUCsvRJ9ykQKBgQDgemz8UFo8kf6jmRZfP8+DbMRmeUz9yJxi7Mq5tp93G0lCACYcMefggq8PCorDND9NyAH1iMOdkGbu/ygXtJeGnGsfe/LxtS2kHquCuN8tU+ziC5yVz6mFB/LpwpP1rpGktfLxPvbJiR5ppdD83trCX5B8hQhPXPeOEakGi4AKdwKBgA1+W5xNQf71IMX6jGHyVM4DJinn/Ztc1VAPKpesvLPs7dudSKWcHhp5z4KvnRlbOwuR+OmSg7bHsdZFqcG/Qs8CFHg1r/3FBHyrmA/YWqu4pAobLz5bqzjCnWbKr+W02q0G4v1SeuYo8uG6oVQNpHJRdBlKbQSwAPrFkp6dCyjRAoGBALC78r6cTM9PappHVzPau3iP0/mSGyncHjRMljetLtPJqd3K3DZqnFNI0KcY97NEmWqVw+UarliJbmFQhrJTPtF+qWB6aAYhTTPf6czb3OqFHwXBeqEAhEj2vyuIRJGzxXtWvVVu60I1MLqhEG9mzfkm4E0JOEezvDwgb2F1x3N1AoGBAMsddYMB1GCSyC+cRT2iR/TOTu4TZ7/SPxia7YMobTzOceXS/3X3V+Jv3rfWxPYXqnhXE5USJYJ7zP01WOgNV8PxwXmYtffswud0wK8VylgzbO9vqVPlqPO1hSonth9N+Jtwd/Pqa/fcw/NrF5ihRKWi9+w3eHCtKXtuLPuUvUV3";

//    @Override
//    protected boolean ignoreExternalCluster() {
//        return true;
//    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(EliatraCloudTresorPlugin.class);
    }

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        Settings.Builder builder = Settings.builder();
        builder.put(super.nodeSettings(nodeOrdinal));
        builder.put(EliatraCloudTresorPluginSettings.CLOUD_TRESOR_ENABLED.name(), true);
        builder.put(EliatraCloudTresorPluginSettings.NODE_PUBLIC_CLUSTER_KEY.name(), PUBLIC_TEST_KEY);
        return builder.build();
    }

    public void testPluginInstalled() throws IOException {
        //client().admin().cluster().health(new ClusterHealthRequest()).actionGet();


        Response response = createRestClient().performRequest(new Request("GET", "/_cat/plugins"));
        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

        logger.info("response body: {}", body);
        assertThat(body, containsString("eliatra-cloud-tresor"));
    }

//    public void testInit() throws IOException {
//        Response response = createRestClient().performRequest(new Request("GET", "/_eliatra/cloud_tresor/api/_get_encrypted_indices"));
//        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
//
//        logger.info("response body: {}", body);
//        assertThat(body, containsString("eliatra-cloud-tresor"));
//    }
}
