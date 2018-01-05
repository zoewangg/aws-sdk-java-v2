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

package software.amazon.awssdk.services.s3.handlers;

import static software.amazon.awssdk.http.Headers.CONTENT_LENGTH;
import static software.amazon.awssdk.http.Headers.CONTENT_MD5;
import static software.amazon.awssdk.services.s3.internal.SkipMd5CheckStrategy.skipClientSideValidation;
import static software.amazon.awssdk.services.s3.internal.SkipMd5CheckStrategy.skipClientSideValidationPerRequest;
import static software.amazon.awssdk.services.s3.internal.SkipMd5CheckStrategy.skipServerSideValidation;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.http.HttpResponse;
import software.amazon.awssdk.core.http.SdkHttpResponseAdapter;
import software.amazon.awssdk.core.interceptor.AwsExecutionAttributes;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.util.Md5Utils;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.Headers;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.services.s3.internal.ChecksumValidationInputStream;
import software.amazon.awssdk.services.s3.internal.Md5DigestCalculatingInputStream;
import software.amazon.awssdk.services.s3.S3AdvancedConfiguration;
import software.amazon.awssdk.services.s3.internal.SkipMd5CheckStrategy;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.utils.BinaryUtils;
import software.amazon.awssdk.utils.IoUtils;
import software.amazon.awssdk.utils.Logger;

/**
 * Interceptor to do client and server side MD5 checksum validation based on requests/response.
 */
public class Md5ValidationInterceptor implements ExecutionInterceptor {

    private static final Logger LOG = Logger.loggerFor(Md5ValidationInterceptor.class);
    private static final String ETAG_HEADER = "ETag";
    private static final Set<Class> CLIENT_SIDE_VALIDATION_PUT_REQUESTS = new HashSet<>(
        Arrays.asList(PutObjectRequest.class, UploadPartRequest.class));

    /**
     * {@inheritDoc}
     *
     * <ol>
     * <li>For streaming operations, if {@link SkipMd5CheckStrategy#skipClientSideValidationPerRequest} is true it wraps the input stream with {@link Md5DigestCalculatingInputStream} to calculate the digest on the fly</li>
     * <li>For non-streaming operations, if {@link SkipMd5CheckStrategy#skipServerSideValidation} is true, it calculates the MD5 value of the content and set the {@link Headers#CONTENT_MD5} header value</li>
     * </ol>
     */
    @Override
    public SdkHttpFullRequest modifyHttpRequest(Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {

        S3AdvancedConfiguration advancedConfiguration =
            (S3AdvancedConfiguration) executionAttributes.getAttribute(AwsExecutionAttributes.SERVICE_ADVANCED_CONFIG);

        if (skipClientSideValidationPerRequest(advancedConfiguration, context.request()) && skipServerSideValidation(context.httpRequest(), advancedConfiguration, context.request())) {
            return context.httpRequest();
        }

        // server side validation
        if (!skipServerSideValidation(context.httpRequest(), advancedConfiguration, context.request()) && context.httpRequest().content().isPresent()) {
            try {
                if (context.httpRequest().content().get().markSupported()) {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    IoUtils.copy(context.httpRequest().content().get(), byteArrayOutputStream);
                    context.httpRequest().content().get().reset();

                    String md5 = Md5Utils.md5AsBase64(byteArrayOutputStream.toByteArray());
                    return context.httpRequest().toBuilder().header(CONTENT_MD5, md5).build();
                }
            } catch (Exception e) {
                throw new SdkClientException("Unable to calculate the MD5 hash of the request", e);
            }
        } else if (!skipClientSideValidationPerRequest(advancedConfiguration, context.request())) {
            if (context.request() instanceof GetObjectRequest) {
                return context.httpRequest().toBuilder()
                              .header("x-amz-te", "append-md5").build();
            }

            if (CLIENT_SIDE_VALIDATION_PUT_REQUESTS.contains(context.request().getClass()) && context.httpRequest().content().isPresent()) {
                Md5DigestCalculatingInputStream md5DigestCalculatingInputStream = new Md5DigestCalculatingInputStream(context.httpRequest().content().get());
                executionAttributes.putAttribute(AwsExecutionAttributes.MESSAGE_DIGEST, md5DigestCalculatingInputStream.getDigest());
                return context.httpRequest().toBuilder().content(md5DigestCalculatingInputStream).build();
            }
        }

        return context.httpRequest();
    }

    /**
     * {@inheritDoc}
     *
     * Server side checksum validation if {@link SkipMd5CheckStrategy#skipClientSideValidation} returns false,
     *
     * <ol>
     * <li>For PUT requests, it compares the ETag value with the message digest of the {@link ExecutionAttributes}</li>
     * <li>For {@link GetObjectRequest}, it wraps the content with {@link ChecksumValidationInputStream} to validate the MD5 value with the trailing checksum value if present</li>
     * </ol>
     */
    @Override
    public SdkHttpFullResponse modifyHttpResponse(Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {

        S3AdvancedConfiguration advancedConfiguration =
            (S3AdvancedConfiguration) executionAttributes.getAttribute(AwsExecutionAttributes.SERVICE_ADVANCED_CONFIG);

        if (skipClientSideValidation(advancedConfiguration, context.request(), context.httpResponse())) {
            return context.httpResponse();
        }

        // GET requests
        if (context.request() instanceof GetObjectRequest && context.httpResponse().content().isPresent() && context.httpResponse().firstMatchingHeader(CONTENT_LENGTH).isPresent()) {
            try {
                System.out.println("modify response");
                InputStream digestValidationInputStream =
                    new ChecksumValidationInputStream(context.httpResponse().content().get(), Integer.valueOf(context.httpResponse().firstMatchingHeader(CONTENT_LENGTH).get()));

                HttpResponse httpResponse = SdkHttpResponseAdapter.adapt(false, context.httpRequest(), context.httpResponse());
                AbortableInputStream inputStream = new AbortableInputStream(digestValidationInputStream, httpResponse);

                return context.httpResponse().toBuilder().content(inputStream).build();
            } catch (NoSuchAlgorithmException e) {
                LOG.warn(() -> "No MD5 digest algorithm available.  Unable to calculate "
                               + "checksum and verify data integrity.", e);
            }
        }

        // PUT requests
        if (CLIENT_SIDE_VALIDATION_PUT_REQUESTS.contains(context.request().getClass()) && executionAttributes.getAttribute(AwsExecutionAttributes.MESSAGE_DIGEST) != null) {

            Optional<String> etag = context.httpResponse().firstMatchingHeader(ETAG_HEADER);
            byte[] serverSideHash = BinaryUtils.fromHex(sanitizeEtag(etag.get()));
            MessageDigest messageDigest = executionAttributes.getAttribute(AwsExecutionAttributes.MESSAGE_DIGEST);
            byte[] clientSideHash = messageDigest.digest();

            if (!Arrays.equals(clientSideHash, serverSideHash)) {
                throw new SdkClientException(
                    String.format("Unable to verify integrity of data uploaded. Client calculated MD5 %s didn't match hash %s calculated by Amazon S3.",
                                  serverSideHash, clientSideHash));
            }
            return context.httpResponse();
        }

        return context.httpResponse();
    }



    private String sanitizeEtag(String etag) {
        return etag.replace("\"", "");
    }
}
