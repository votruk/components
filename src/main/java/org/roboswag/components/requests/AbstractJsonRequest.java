/*
 *  Copyright (c) 2015 RoboSwag (Gavriil Sitnikov, Vsevolod Ivanov)
 *
 *  This file is part of RoboSwag library.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.roboswag.components.requests;

import android.support.annotation.NonNull;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ObjectParser;

/**
 * Created by Gavriil Sitnikov on 07/14.
 * Request that returns data in JSON format
 */
public abstract class AbstractJsonRequest<T> extends AbstractHttpRequest<T> {

    protected static final JsonFactory DEFAULT_JSON_FACTORY = new JacksonFactory();

    @NonNull
    @Override
    protected ObjectParser getParser() {
        return DEFAULT_JSON_FACTORY.createJsonObjectParser();
    }

    protected AbstractJsonRequest(@NonNull final Class<T> responseResultType) {
        super(responseResultType);
    }

}