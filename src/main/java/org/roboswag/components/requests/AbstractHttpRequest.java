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
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.util.Charsets;
import com.google.api.client.util.ObjectParser;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import org.roboswag.core.log.Lc;
import org.roboswag.core.log.LcHelper;
import org.roboswag.core.utils.ShouldNotHappenException;
import org.roboswag.core.utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import okio.Buffer;
import rx.Observable;
import rx.schedulers.Schedulers;

/**
 * Created by Gavriil Sitnikov on 13/11/2015.
 * TODO: fill description
 */
public abstract class AbstractHttpRequest<T> {

    private static final String CACHE_PARAMETER_SEPARATOR = "#";
    private static final int CACHE_MAX_KEY_SIZE = 128;

    @NonNull
    private static Charset getCharset(final ResponseBody responseBody) {
        final MediaType contentType = responseBody.contentType();
        return contentType == null ? Charsets.UTF_8 : contentType.charset(Charsets.UTF_8);
    }

    private static String requestBodyToString(@NonNull final Request request) throws IOException {
        final RequestBody body = request.newBuilder().build().body();
        if (body == null) {
            return "";
        }
        final Buffer buffer = new Buffer();
        body.writeTo(buffer);
        return buffer.readUtf8();
    }

    @NonNull
    private final Class<T> responseResultType;

    private Request request;
    private Call call;

    protected AbstractHttpRequest(@NonNull final Class<T> responseResultType) {
        this.responseResultType = responseResultType;
    }

    @NonNull
    public Class<T> getResponseResultType() {
        return responseResultType;
    }

    /* Returns data parser */
    protected abstract ObjectParser getParser() throws Exception;

    @NonNull
    protected abstract String getUrl();

    protected void setupUrlParameters(@NonNull final GenericUrl url) {
        // to be overridden. default does nothing
    }

    protected void setupHeaders(@NonNull final Map<String, String> headers) {
        // to be overridden. default does nothing
    }

    @NonNull
    protected OkHttpClient createHttpClient() {
        return new OkHttpClient();
    }

    @NonNull
    protected Request.Builder createHttpRequest() throws IOException {
        final GenericUrl genericUrl = new GenericUrl(getUrl());
        final Map<String, String> headers = new HashMap<>();
        setupHeaders(headers);
        setupUrlParameters(genericUrl);
        return new Request.Builder().url(genericUrl.build()).headers(Headers.of(headers));
    }

    @NonNull
    private Request getRequest() throws IOException {
        if (request == null) {
            request = createHttpRequest().build();
        }
        return request;
    }

    public void cancel() {
        if (call != null && !call.isCanceled()) {
            call.cancel();
        }
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public T executeSync() throws Exception {
        final Request request = getRequest();
        if (LcHelper.getLogLevel() <= Log.DEBUG) {
            Lc.d("Url requested: %s\n%s", request.url(), requestBodyToString(request));
        }
        call = createHttpClient().newCall(request);
        final Response response = call.execute();
        final ResponseBody responseBody = response.body();
        final byte[] bytes = responseBody.bytes();
        final Charset charset = getCharset(responseBody);
        if (LcHelper.getLogLevel() <= Log.DEBUG) {
            Lc.d("Response for: %s has code %s and content: %s", request.url(), response.code(), new String(bytes, charset));
        }
        if (getResponseResultType().equals(Response.class)) {
            return handleResponse((T) response);
        }
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        final T result;
        try {
            result = getParser().parseAndClose(byteArrayInputStream, charset, responseResultType);
        } catch (final RuntimeException throwable) {
            throw new ShouldNotHappenException("Runtime exception during response parsing " + getUrl(), throwable);
        } catch (final JsonProcessingException exception) {
            throw new ShouldNotHappenException("Parsing exception during response parsing " + getUrl(), exception);
        }
        if (result == null) {
            throw new ShouldNotHappenException("Response is null for request " + getUrl());
        }
        return handleResponse(result);
    }

    @NonNull
    public Observable<T> execute() {
        return Observable.<T>create(subscriber -> {
            try {
                subscriber.onNext(executeSync());
                subscriber.onCompleted();
            } catch (Exception e) {
                subscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io())
                .doOnUnsubscribe(this::cancel)
                .unsubscribeOn(Schedulers.io());
    }

    /* Handle response. Use it to do something after request successfully executes */
    @NonNull
    protected T handleResponse(@NonNull final T response) throws Exception {
        return response;
    }

    @NonNull
    public String getCacheKey() {
        final StringBuilder fileNameSafeCacheKey = new StringBuilder();
        try {
            final Request request = getRequest();
            fileNameSafeCacheKey.append(URLEncoder.encode(request.urlString(), "UTF-8").replace("%", CACHE_PARAMETER_SEPARATOR));
            fileNameSafeCacheKey.append(CACHE_PARAMETER_SEPARATOR).append(request.headers());
            if (request.body() != null) {
                fileNameSafeCacheKey.append(CACHE_PARAMETER_SEPARATOR).append(requestBodyToString(request));
            }
        } catch (final Exception e) {
            throw new ShouldNotHappenException(e);
        }
        final String cacheKeyMd5 = StringUtils.md5(fileNameSafeCacheKey.toString());
        final int length = fileNameSafeCacheKey.length();
        return fileNameSafeCacheKey.substring(Math.max(0, length - CACHE_MAX_KEY_SIZE), length) + CACHE_PARAMETER_SEPARATOR + cacheKeyMd5;
    }

}