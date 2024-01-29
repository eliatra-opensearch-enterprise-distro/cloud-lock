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

import org.opensearch.action.ActionType;

public class UpdateKeyAction extends ActionType<UpdateKeyResponse> {

    public static final UpdateKeyAction INSTANCE = new UpdateKeyAction();
    public static final String NAME = "cluster:admin/eliatra_ct/update_key";

    protected UpdateKeyAction() {
        super(NAME, UpdateKeyResponse::new);
    }
}
