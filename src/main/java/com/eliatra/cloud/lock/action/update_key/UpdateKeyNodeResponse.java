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

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;

public class UpdateKeyNodeResponse extends BaseNodeResponse {

    private final boolean isMaster;
    private final boolean keySet;
    private final boolean keySaved;
    
    public UpdateKeyNodeResponse(StreamInput in) throws IOException {
        super(in);
        this.isMaster = in.readBoolean();
        this.keySet = in.readBoolean();
        this.keySaved = in.readBoolean();
    }

    public UpdateKeyNodeResponse(DiscoveryNode node, boolean isMaster, boolean keySet, boolean keySaved) {
        super(node);
        this.isMaster = isMaster;
        this.keySet = keySet;
        this.keySaved = keySaved;
    }

    public static UpdateKeyNodeResponse readNodeResponse(StreamInput in) throws IOException {
        return new UpdateKeyNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(isMaster);
        out.writeBoolean(keySet);
        out.writeBoolean(keySaved);
    }

    public boolean isMaster() {
        return isMaster;
    }

    public boolean isKeySet() {
        return keySet;
    }

    public boolean isKeySaved() {
        return keySaved;
    }
}
