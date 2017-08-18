/*
 * Copyright 2010-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.http.async.netty;

import static software.amazon.awssdk.http.SdkHttpConfigurationOption.CONNECTION_TIMEOUT;
import static software.amazon.awssdk.http.SdkHttpConfigurationOption.MAX_CONNECTIONS;
import static software.amazon.awssdk.http.SdkHttpConfigurationOption.SOCKET_TIMEOUT;
import static software.amazon.awssdk.http.SdkHttpConfigurationOption.USE_STRICT_HOSTNAME_VERIFICATION;
import static software.amazon.awssdk.utils.FunctionalUtils.invokeSafely;
import static software.amazon.awssdk.utils.NumericUtils.saturatedCast;

import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseHeaders;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.RequestBuilder;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.annotation.ReviewBeforeRelease;
import software.amazon.awssdk.annotation.SdkInternalApi;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkRequestContext;
import software.amazon.awssdk.http.async.AbortableRunnable;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkHttpRequestProvider;
import software.amazon.awssdk.http.async.SdkHttpResponseHandler;
import software.amazon.awssdk.http.async.SimpleSubscriber;
import software.amazon.awssdk.utils.AttributeMap;

@SdkInternalApi
final class NettySdkAsyncHttpClient implements SdkAsyncHttpClient {

    private static final EpollEventLoopGroup eventLoop = new EpollEventLoopGroup(100);
    private final ServiceDefaults serviceDefaults;
    private final AsyncHttpClient httpClient;

    NettySdkAsyncHttpClient(NettySdkHttpClientFactory factory, AttributeMap serviceDefaultsMap) {
        httpClient = new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                                                        .setConnectTimeout(saturatedCast(
                                                                serviceDefaultsMap.get(CONNECTION_TIMEOUT).toMillis()))
                                                        .setMaxConnections(serviceDefaultsMap.get(MAX_CONNECTIONS))
                                                        .setMaxConnectionsPerHost(serviceDefaultsMap.get(MAX_CONNECTIONS))
                                                        .addChannelOption(ChannelOption.SO_BACKLOG, 1024)
                                                        .setEventLoopGroup(eventLoop)
                                                        .setMaxRequestRetry(0)
                                                        .build());
        this.serviceDefaults = new ServiceDefaults(serviceDefaultsMap);
    }

    @Override
    public AbortableRunnable prepareRequest(SdkHttpRequest sdkRequest,
                                            SdkRequestContext requestC0ontext,
                                            SdkHttpRequestProvider requestProvider,
                                            SdkHttpResponseHandler handler) {
        return new AbortableRunnable() {
            private ListenableFuture<Void> future;

            @Override
            public void run() {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                requestProvider.subscribe(new SimpleSubscriber(b -> invokeSafely(() -> baos.write(b.array()))));
                this.future = httpClient
                        .prepareRequest(new RequestBuilder(sdkRequest.getHttpMethod().toString(), false, false)
                                                .setBody(baos.toByteArray())
                                                .setHeaders(sdkRequest.getHeaders())
                                                .setQueryParams(sdkRequest.getParameters())
                                                .setUrl(sdkRequest.getEndpoint().toString()))
                        .execute(new HandlerAdapter(handler));

            }

            @Override
            public void abort() {
                if (future != null) {
                    future.abort(new RuntimeException("Timed out"));
                }
            }
        };
    }

    @Override
    public <T> Optional<T> getConfigurationValue(SdkHttpConfigurationOption<T> key) {
        return serviceDefaults.getConfigurationValue(key);
    }

    @Override
    public void close() throws Exception {
        httpClient.close();
    }

    /**
     * Helper class to unwrap and convert service defaults.
     */
    private static class ServiceDefaults {
        private final AttributeMap serviceDefaults;

        private ServiceDefaults(AttributeMap serviceDefaults) {
            this.serviceDefaults = serviceDefaults;
        }

        @ReviewBeforeRelease("Not sure if Netty supports setting socket timeout. There's a ReadTimeoutHandler but that" +
                             "fires if the connection is just idle which is not what we want.")
        public int getSocketTimeout() {
            return saturatedCast(serviceDefaults.get(SOCKET_TIMEOUT).toMillis());
        }

        public int getConnectionTimeout() {
            return saturatedCast(serviceDefaults.get(CONNECTION_TIMEOUT).toMillis());
        }

        @ReviewBeforeRelease("Does it make sense to use this value? Netty's implementation is max connections" +
                             " per endpoint so if it's a shared client it doesn't mean quite the same thing.")
        public int getMaxConnections() {
            return serviceDefaults.get(MAX_CONNECTIONS);
        }

        @ReviewBeforeRelease("Support disabling strict hostname verification")
        public <T> Optional<T> getConfigurationValue(AttributeMap.Key<T> key) {
            return key == USE_STRICT_HOSTNAME_VERIFICATION ? Optional.empty() :
                    Optional.ofNullable(serviceDefaults.get(key));
        }
    }

    private static class HandlerAdapter implements AsyncHandler<Void> {

        private final SdkHttpFullResponse.Builder builder;
        private final SdkHttpResponseHandler handler;
        private Subscriber<? super ByteBuffer> subscriber;

        private HandlerAdapter(SdkHttpResponseHandler handler) {
            this.handler = handler;
            builder = SdkHttpFullResponse.builder();
        }

        @Override
        public void onThrowable(Throwable throwable) {
            handler.exceptionOccurred(throwable);
        }

        @Override
        public State onBodyPartReceived(HttpResponseBodyPart httpResponseBodyPart) throws Exception {
            if (subscriber == null) {
                handler.onStream(new Publisher<ByteBuffer>() {
                    @Override
                    public void subscribe(Subscriber<? super ByteBuffer> s) {
                        subscriber = s;
                    }
                });
            }
            subscriber.onNext(httpResponseBodyPart.getBodyByteBuffer());
            return State.CONTINUE;
        }

        @Override
        public State onStatusReceived(HttpResponseStatus httpResponseStatus) throws Exception {
            builder.statusText(httpResponseStatus.getStatusText())
                   .statusCode(httpResponseStatus.getStatusCode());
            return State.CONTINUE;
        }

        @Override
        public State onHeadersReceived(HttpResponseHeaders httpResponseHeaders) throws Exception {
            Map<String, List<String>> headers = new HashMap<>();
            for (Map.Entry<String, String> header : httpResponseHeaders.getHeaders()) {
                headers.computeIfAbsent(header.getKey(), s -> new ArrayList<>());
                headers.get(header.getKey()).add(header.getValue());
            }
            handler.headersReceived(builder.headers(headers).build());
            return State.CONTINUE;
        }

        @Override
        public Void onCompleted() throws Exception {
            handler.complete();
            return null;
        }

        private static class SubscriberAdapter implements Subscriber<HttpResponseBodyPart> {
            private final Subscriber<? super ByteBuffer> subscriber;

            private SubscriberAdapter(Subscriber<? super ByteBuffer> subscriber) {
                this.subscriber = subscriber;
            }

            @Override
            public void onSubscribe(Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(HttpResponseBodyPart httpResponseBodyPart) {
                subscriber.onNext(httpResponseBodyPart.getBodyByteBuffer());
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        }

        private static class PublisherAdapter implements Publisher<ByteBuffer> {
            private final Publisher<HttpResponseBodyPart> publisher;

            private PublisherAdapter(Publisher<HttpResponseBodyPart> publisher) {
                this.publisher = publisher;
            }

            @Override
            public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
                publisher.subscribe(new SubscriberAdapter(subscriber));
            }
        }
    }
}
