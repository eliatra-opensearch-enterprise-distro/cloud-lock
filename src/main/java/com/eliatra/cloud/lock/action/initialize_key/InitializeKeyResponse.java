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

import com.eliatra.cloud.lock.action.update_key.UpdateKeyResponse;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

public class InitializeKeyResponse extends AcknowledgedResponse implements ToXContentObject {

    private final UpdateKeyResponse updateKeyResponse;

    public InitializeKeyResponse(StreamInput in) throws IOException {
        super(in);
        updateKeyResponse = in.readOptionalWriteable(UpdateKeyResponse::new);
    }

    public InitializeKeyResponse(UpdateKeyResponse updateKeyResponse) {
        super(true);
        this.updateKeyResponse = updateKeyResponse;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalWriteable(updateKeyResponse);
    }

    public UpdateKeyResponse getUpdateKeyResponse() {
        return updateKeyResponse;
    }

    @Override
    protected void addCustomFields(XContentBuilder builder, Params params) throws IOException {
        builder.field("response", updateKeyResponse);
    }
}
