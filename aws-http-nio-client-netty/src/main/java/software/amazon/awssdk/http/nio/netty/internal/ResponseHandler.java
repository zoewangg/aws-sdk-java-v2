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

package software.amazon.awssdk.http.nio.netty.internal;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static software.amazon.awssdk.http.nio.netty.internal.ChannelAttributeKeys.PUBLISHER_KEY;
import static software.amazon.awssdk.http.nio.netty.internal.ChannelAttributeKeys.REQUEST_CONTEXT_KEY;

import com.typesafe.netty.http.HttpStreamsClientHandler;
import com.typesafe.netty.http.StreamedHttpResponse;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.utils.Logger;

@Sharable
class ResponseHandler extends SimpleChannelInboundHandler<HttpObject> {

    /**
     * {@link AttributeKey} to keep track of whether we should close the connection after this request
     * has completed.
     */
    private static final AttributeKey<Boolean> KEEP_ALIVE = AttributeKey.newInstance("KeepAlive");

    private static final Logger log = Logger.loggerFor(ResponseHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext channelContext, HttpObject msg) throws Exception {
        RequestContext requestContext = channelContext.channel().attr(REQUEST_CONTEXT_KEY).get();

        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            SdkHttpResponse sdkResponse = SdkHttpFullResponse.builder()
                                                             .headers(fromNettyHeaders(response.headers()))
                                                             .statusCode(response.status().code())
                                                             .statusText(response.status().reasonPhrase())
                                                             .build();
            channelContext.channel().attr(KEEP_ALIVE).set(HttpUtil.isKeepAlive(response));
            requestContext.handler().headersReceived(sdkResponse);
        }
        if (channelContext.channel().attr(PUBLISHER_KEY).get() == null) {
            requestContext.handler().onStream(new Publisher<ByteBuffer>() {
                @Override
                public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
                    channelContext.channel().attr(ChannelAttributeKeys.PUBLISHER_KEY).set(new Subscriber<ByteBuffer>() {
                        @Override
                        public void onSubscribe(Subscription subscription) {
                            subscriber.onSubscribe(subscription);
                        }

                        @Override
                        public void onNext(ByteBuffer byteBuffer) {
                            subscriber.onNext(byteBuffer);

                        }

                        @Override
                        public void onError(Throwable throwable) {
                            subscriber.onError(throwable);
                        }

                        @Override
                        public void onComplete() {
                            subscriber.onComplete();
                            requestContext.handler().complete();
                        }
                    });
                }
            });
        }

        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;
            if (content.content().readableBytes() > 0) {
                channelContext.channel().attr(ChannelAttributeKeys.PUBLISHER_KEY).get()
                              .onNext(content.content().nioBuffer());
            }
            if (msg instanceof LastHttpContent) {
                try {
                    channelContext.channel().attr(ChannelAttributeKeys.PUBLISHER_KEY).get()
                                  .onComplete();
                    if (!channelContext.channel().attr(KEEP_ALIVE).get()) {
                        channelContext.channel().close();
                    }
                } finally {
                    requestContext.channelPool().release(channelContext.channel());
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        RequestContext requestContext = ctx.channel().attr(REQUEST_CONTEXT_KEY).get();
        log.error(() -> "Exception processing request: " + requestContext.sdkRequest(), cause);
        requestContext.handler().exceptionOccurred(cause);
        requestContext.channelPool().close();
        requestContext.channelPool().release(ctx.channel());
        ctx.fireExceptionCaught(cause);
    }

    private static Map<String, List<String>> fromNettyHeaders(HttpHeaders headers) {
        return headers.entries().stream()
                      .collect(groupingBy(Map.Entry::getKey,
                                          mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static class PublisherAdapter implements Publisher<ByteBuffer> {
        private final StreamedHttpResponse response;
        private final ChannelHandlerContext channelContext;
        private final RequestContext requestContext;

        private PublisherAdapter(StreamedHttpResponse response, ChannelHandlerContext channelContext,
                                 RequestContext requestContext) {
            this.response = response;
            this.channelContext = channelContext;
            this.requestContext = requestContext;
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
            response.subscribe(new Subscriber<HttpContent>() {
                @Override
                public void onSubscribe(Subscription subscription) {
                    subscriber.onSubscribe(subscription);
                }

                @Override
                public void onNext(HttpContent httpContent) {
                    subscriber.onNext(httpContent.content().nioBuffer());
                    httpContent.release();
                    channelContext.read();
                }

                @Override
                public void onError(Throwable t) {
                    subscriber.onError(t);
                    requestContext.handler().exceptionOccurred(t);
                }

                @Override
                public void onComplete() {
                    subscriber.onComplete();
                    requestContext.handler().complete();
                    if (!channelContext.channel().attr(KEEP_ALIVE).get()) {
                        channelContext.channel().close();
                    }
                    channelContext.pipeline().remove(HttpStreamsClientHandler.class);
                    channelContext.pipeline().remove(ResponseHandler.class);
                    requestContext.channelPool().release(channelContext.channel());
                }
            });
        }
    }
}
