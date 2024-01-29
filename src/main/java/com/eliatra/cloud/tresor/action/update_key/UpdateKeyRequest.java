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

package com.eliatra.cloud.tresor.action.update_key;

import com.eliatra.cloud.tresor.crypto.SymmetricKek;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;

public class UpdateKeyRequest extends BaseNodesRequest<UpdateKeyRequest> {

    private SymmetricKek symmetricKek;

    public UpdateKeyRequest(StreamInput in) throws IOException {
        super(in);
        this.symmetricKek = new SymmetricKek(in);
    }

    public UpdateKeyRequest() {
        super(new String[0]);
    }
    
    public UpdateKeyRequest(SymmetricKek symmetricKek) {
        this();
        setKey(symmetricKek);
    }

    @Override
    public void writeTo(final StreamOutput out) throws IOException {
        super.writeTo(out);
        symmetricKek.writeTo(out);
    }

    public SymmetricKek getKey() {
        return symmetricKek;
    }

    public void setKey(SymmetricKek symmetricKek) {
        this.symmetricKek = symmetricKek;
    }

    @Override
    public ActionRequestValidationException validate() {
        if (symmetricKek == null) {
            return new ActionRequestValidationException();
        }
        return null;
    }
}
