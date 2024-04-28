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

package com.eliatra.cloud.lock.action.update_key;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.nodes.BaseNodesResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.common.xcontent.StatusToXContentObject;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;

public class UpdateKeyResponse extends BaseNodesResponse<UpdateKeyNodeResponse> implements StatusToXContentObject {

    public UpdateKeyResponse(StreamInput in) throws IOException {
        super(in);
    }
    
    public UpdateKeyResponse(final ClusterName clusterName, List<UpdateKeyNodeResponse> nodes, List<FailedNodeException> failures) {
        super(clusterName, nodes, failures);
    }

    @Override
    public List<UpdateKeyNodeResponse> readNodesFrom(final StreamInput in) throws IOException {
        return in.readList(UpdateKeyNodeResponse::readNodeResponse);
    }

    @Override
    public void writeNodesTo(final StreamOutput out, List<UpdateKeyNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public RestStatus status() {
        return failures().isEmpty()?RestStatus.OK:RestStatus.INTERNAL_SERVER_ERROR;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.startObject("nodes");
        for (UpdateKeyNodeResponse node : getNodes()) {
            builder.startObject(node.getNode().getId());
            builder.field("nodeName", node.getNode().getName());
            builder.field("isMaster", node.isMaster());
            builder.field("keySet", node.isKeySet());
            builder.field("keySaved", node.isKeySaved());
            builder.field("failures", failures().toString());
            builder.endObject();
        }
        builder.endObject();
        builder.endObject();

        return builder;
    }
}
