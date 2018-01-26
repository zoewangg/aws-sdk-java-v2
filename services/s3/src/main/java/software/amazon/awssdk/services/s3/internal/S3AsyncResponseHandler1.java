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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import software.amazon.awssdk.core.async.AsyncResponseHandler;

/**
 * S3 Callback interface to handle a streaming asynchronous response.
 *
 * @param <ResponseT> POJO response type.
 * @param <ReturnT> Type this response handler produces. I.E. the type you are transforming the response into.
 */
public interface S3AsyncResponseHandler1<ResponseT, ReturnT> extends AsyncResponseHandler<ResponseT, ReturnT> {

    /**
     * Creates an {@link AsyncResponseHandler} that writes all the content to the given file. In the event of an error,
     * the SDK will attempt to delete the file (whatever has been written to it so far). If the file already exists, an
     * exception will be thrown.
     *
     * @param path        Path to file to write to.
     * @param <ResponseT> Pojo Response type.
     * @return AsyncResponseHandler instance.
     */
    static <ResponseT> AsyncResponseHandler<ResponseT, ResponseT> toFile(Path path) {
        return new S3FileAsyncResponseHandler<>(path);
    }

    /**
     * Creates an {@link AsyncResponseHandler} that writes all content to a byte array.
     *
     * @param <ResponseT> Pojo response type.
     * @return AsyncResponseHandler instance.
     */
    static <ResponseT> AsyncResponseHandler<ResponseT, byte[]> toByteArray() {
        return new S3ByteArrayAsyncResponseHandler<>();
    }

//    /**
//     * Creates an {@link AsyncResponseHandler} that writes all content to a string using the specified encoding.
//     *
//     * @param charset     {@link Charset} to use when constructing the string.
//     * @param <ResponseT> Pojo response type.
//     * @return AsyncResponseHandler instance.
//     */
//    static <ResponseT> AsyncResponseHandler<ResponseT, String> toString(Charset charset) {
//        return new S3StringAsyncResponseHandler<>(toByteArray(), charset);
//    }
//
//    /**
//     * Creates an {@link AsyncResponseHandler} that writes all content to UTF8 encoded string.
//     *
//     * @param <ResponseT> Pojo response type.
//     * @return AsyncResponseHandler instance.
//     */
//    static <ResponseT> AsyncResponseHandler<ResponseT, String> toUtf8String() {
//        return toString(StandardCharsets.UTF_8);
//    }
}
