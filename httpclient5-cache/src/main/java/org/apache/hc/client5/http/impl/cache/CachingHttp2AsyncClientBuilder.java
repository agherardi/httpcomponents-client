/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.http.impl.cache;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.cache.HttpCacheInvalidator;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.impl.ChainElements;
import org.apache.hc.client5.http.impl.async.Http2AsyncClientBuilder;
import org.apache.hc.core5.http.config.NamedElementChain;

/**
 * Builder for HTTP/2 {@link org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient}
 * instances capable of client-side caching.
 *
 * @since 5.0
 */
public class CachingHttp2AsyncClientBuilder extends Http2AsyncClientBuilder {

    private ResourceFactory resourceFactory;
    private HttpCacheStorage storage;
    private File cacheDir;
    private CacheConfig cacheConfig;
    private HttpCacheInvalidator httpCacheInvalidator;
    private boolean deleteCache;

    public static CachingHttp2AsyncClientBuilder create() {
        return new CachingHttp2AsyncClientBuilder();
    }

    protected CachingHttp2AsyncClientBuilder() {
        super();
        this.deleteCache = true;
    }

    public final CachingHttp2AsyncClientBuilder setResourceFactory(
            final ResourceFactory resourceFactory) {
        this.resourceFactory = resourceFactory;
        return this;
    }

    public final CachingHttp2AsyncClientBuilder setHttpCacheStorage(
            final HttpCacheStorage storage) {
        this.storage = storage;
        return this;
    }

    public final CachingHttp2AsyncClientBuilder setCacheDir(
            final File cacheDir) {
        this.cacheDir = cacheDir;
        return this;
    }

    public final CachingHttp2AsyncClientBuilder setCacheConfig(
            final CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
        return this;
    }

    public final CachingHttp2AsyncClientBuilder setHttpCacheInvalidator(
            final HttpCacheInvalidator cacheInvalidator) {
        this.httpCacheInvalidator = cacheInvalidator;
        return this;
    }

    public CachingHttp2AsyncClientBuilder setDeleteCache(final boolean deleteCache) {
        this.deleteCache = deleteCache;
        return this;
    }

    @Override
    protected void customizeExecChain(final NamedElementChain<AsyncExecChainHandler> execChainDefinition) {
        final CacheConfig config = this.cacheConfig != null ? this.cacheConfig : CacheConfig.DEFAULT;
        // We copy the instance fields to avoid changing them, and rename to avoid accidental use of the wrong version
        ResourceFactory resourceFactoryCopy = this.resourceFactory;
        if (resourceFactoryCopy == null) {
            if (this.cacheDir == null) {
                resourceFactoryCopy = new HeapResourceFactory();
            } else {
                resourceFactoryCopy = new FileResourceFactory(cacheDir);
            }
        }
        HttpCacheStorage storageCopy = this.storage;
        if (storageCopy == null) {
            if (this.cacheDir == null) {
                storageCopy = new BasicHttpCacheStorage(config);
            } else {
                final ManagedHttpCacheStorage managedStorage = new ManagedHttpCacheStorage(config);
                if (this.deleteCache) {
                    addCloseable(new Closeable() {

                        @Override
                        public void close() throws IOException {
                            managedStorage.shutdown();
                        }

                    });
                } else {
                    addCloseable(managedStorage);
                }
                storageCopy = managedStorage;
            }
        }
        final CacheKeyGenerator uriExtractor = new CacheKeyGenerator();
        final HttpCache httpCache = new BasicHttpCache(
                resourceFactoryCopy,
                storageCopy,
                uriExtractor,
                this.httpCacheInvalidator != null ? this.httpCacheInvalidator : new DefaultCacheInvalidator(uriExtractor, storageCopy));

        final AsyncCachingExec cachingExec = new AsyncCachingExec(httpCache, config);
        execChainDefinition.addBefore(ChainElements.PROTOCOL.name(), cachingExec, ChainElements.CACHING.name());
    }

}
