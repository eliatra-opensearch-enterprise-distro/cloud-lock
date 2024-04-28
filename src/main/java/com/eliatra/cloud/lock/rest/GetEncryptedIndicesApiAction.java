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

package com.eliatra.cloud.lock.rest;

import com.eliatra.cloud.lock.plugin.EliatraCloudLockPluginSettings;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.opensearch.rest.RestRequest.Method.GET;

public class GetEncryptedIndicesApiAction extends BaseRestHandler {

    private final ClusterService clusterService;
    private final ThreadContext threadContext;

    public GetEncryptedIndicesApiAction(ClusterService clusterService, ThreadContext threadContext) {
        this.clusterService = clusterService;
        this.threadContext = threadContext;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(GET, "/_eliatra/cloud_lock/api/_get_encrypted_indices"));
    }

    @Override
    public String getName() {
        return "Get Encrypted Indices Action";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        return channel -> {
            XContentBuilder builder = channel.newBuilder(); //NOSONAR
            BytesRestResponse response = null;

            try {

                Metadata clusterMetadata = clusterService.state().metadata();

                List<IndexMetadata> encryptedIndices = new ArrayList<>();

                Arrays.stream(clusterMetadata.getConcreteAllIndices()).forEach(
                        index -> {
                            IndexMetadata indexMetadata = clusterMetadata.index(index);
                            if(EliatraCloudLockPluginSettings.INDEX_ENCRYPTION_ENABLED.getFrom(
                                indexMetadata.getSettings()
                            )) {
                                encryptedIndices.add(clusterMetadata.index(index));
                            }
                        }
                );

                builder.startObject();

                for(IndexMetadata indexMetadata: encryptedIndices) {
                    builder.startObject(indexMetadata.getIndexUUID());
                    builder.field("name", indexMetadata.getIndex().getName());
                    builder.field("store_type_original", EliatraCloudLockPluginSettings.INDEX_STORETYPE_ORIGINAL.getFrom(indexMetadata.getSettings()));
                    builder.endObject();
                }
                builder.endObject();

                response = new BytesRestResponse(RestStatus.OK, builder);
            } catch (final Exception e1) {
                builder = channel.newBuilder(); //NOSONAR
                builder.startObject();
                builder.field("error", e1.toString());
                builder.endObject();
                response = new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, builder);
            } finally {
                if (builder != null) {
                    builder.close();
                }
            }

            channel.sendResponse(response);
        };

    }
}