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

package com.eliatra.cloud.lock.action.initialize_key;

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;

public class InitializeKeyNodeResponse extends BaseNodeResponse {
    
    private int keyResponse;
    private String message;
    
    public InitializeKeyNodeResponse(StreamInput in) throws IOException {
        super(in);
        keyResponse = in.readInt();
        message = in.readOptionalString();
    }

    public InitializeKeyNodeResponse(final DiscoveryNode node, int keyResponse, String message) {
        super(node);
        this.keyResponse = keyResponse;
        this.message = message;
    }
    
    public static InitializeKeyNodeResponse readNodeResponse(StreamInput in) throws IOException {
        return new InitializeKeyNodeResponse(in);
    }

    public int getKeyResponse() {
        return keyResponse;
    }

    public String getMessage() {
        return message;
    }
    
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeInt(keyResponse);
        out.writeOptionalString(message);
    }

    @Override
    public String toString() {
        return "InitializeKeyNodeResponse{" +
                "keyResponse=" + keyResponse +
                ", message='" + message + '\'' +
                '}';
    }
}
