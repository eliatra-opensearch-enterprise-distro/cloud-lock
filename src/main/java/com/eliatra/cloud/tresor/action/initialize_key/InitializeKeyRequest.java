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

package com.eliatra.cloud.tresor.action.initialize_key;

import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.support.master.MasterNodeRequest;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;

public class InitializeKeyRequest extends MasterNodeRequest<InitializeKeyRequest> {

    private byte[] base64EncodedPrivateRsaKey;

    public InitializeKeyRequest(StreamInput in) throws IOException {
        super(in);
        this.base64EncodedPrivateRsaKey = in.readByteArray();
    }

    public InitializeKeyRequest() {

    }

    public InitializeKeyRequest(byte[] base64EncodedPrivateRsaKey) {
        setKey(base64EncodedPrivateRsaKey);
    }

    @Override
    public void writeTo(final StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeByteArray(base64EncodedPrivateRsaKey);
    }

    public byte[] getKey() {
        return base64EncodedPrivateRsaKey;
    }

    public void setKey(byte[] base64EncodedPrivateRsaKey) {
        this.base64EncodedPrivateRsaKey = base64EncodedPrivateRsaKey;
    }

    @Override
    public ActionRequestValidationException validate() {
        if (base64EncodedPrivateRsaKey == null) {
            return new ActionRequestValidationException();
        }
        return null;
    }
}
