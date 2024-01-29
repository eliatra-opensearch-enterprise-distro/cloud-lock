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

package com.eliatra.cloud.tresor.rest;

import com.eliatra.cloud.tresor.action.initialize_key.InitializeKeyAction;
import com.eliatra.cloud.tresor.action.initialize_key.InitializeKeyRequest;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.opensearch.rest.RestRequest.Method.POST;

public class InitializeKeyApiAction extends BaseRestHandler {

    private final ClusterService clusterService;
    private final ThreadContext threadContext;

    public InitializeKeyApiAction(ClusterService clusterService, ThreadContext threadContext) {
        this.clusterService = clusterService;
        this.threadContext = threadContext;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(POST, "/_eliatra/cloud_tresor/api/_initialize_key"));
    }

    @Override
    public String getName() {
        return "Initialize Key Action";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        final BytesReference content = request.requiredContent();
        final Map<String, Object> map = XContentHelper.convertToMap(content, false, request.getMediaType()).v2();
        return channel -> client.execute(InitializeKeyAction.INSTANCE, new InitializeKeyRequest(((String)map.get("key")).getBytes(StandardCharsets.UTF_8)), new RestToXContentListener<>(channel));
    }
}