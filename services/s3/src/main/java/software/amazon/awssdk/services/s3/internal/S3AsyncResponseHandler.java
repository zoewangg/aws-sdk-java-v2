/*
 * Copyright 2010-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.awssdk.services.s3.internal;

import static software.amazon.awssdk.utils.FunctionalUtils.invokeSafely;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.AsyncResponseHandler;
import software.amazon.awssdk.utils.BinaryUtils;

/**
 * S3 Callback interface to handle a streaming asynchronous response.
 *
 * @param <ResponseT> POJO response type.
 * @param <ReturnT>   Type this response handler produces. I.E. the type you are transforming the response into.
 */
public interface S3AsyncResponseHandler<ResponseT, ReturnT> extends AsyncResponseHandler<ResponseT, ReturnT> {



//    private ByteArrayOutputStream baos;
//
//    private AsyncResponseHandler<ResponseT, ReturnT> asyncResponseHandler;
//
//    public S3AsyncResponseHandler(AsyncResponseHandler asyncResponseHandler) {
//        this.asyncResponseHandler = asyncResponseHandler;
//    }
//
//    @Override
//    public void responseReceived(ResponseT response) {
//        asyncResponseHandler.responseReceived(response);
//    }
//
//    @Override
//    public void exceptionOccurred(Throwable throwable) {
//        asyncResponseHandler.exceptionOccurred(throwable);
//    }
//
//    @Override
//    public ReturnT complete() {
//        return asyncResponseHandler.complete();
//    }
//
//    @Override
//    public void onStream(Publisher<ByteBuffer> publisher) {
//        baos = new ByteArrayOutputStream();
//        publisher.subscribe(new BaosSubscriber());
//    }

//    /**
//     * Requests chunks sequentially and dumps them into a {@link ByteArrayOutputStream}.
//     */
//    private class BaosSubscriber implements Subscriber<ByteBuffer> {
//
//        private Subscription subscription;
//
//        @Override
//        public void onSubscribe(Subscription s) {
//            this.subscription = s;
//            subscription.request(1);
//        }
//
//        @Override
//        public void onNext(ByteBuffer byteBuffer) {
//            invokeSafely(() -> baos.write(BinaryUtils.copyBytesFrom(byteBuffer)));
//            subscription.request(1);
//        }
//
//        @Override
//        public void onError(Throwable throwable) {
//            // Handled by response handler
//        }
//
//        @Override
//        public void onComplete() {
//            // Handled by response handler
//        }
//    }
}
