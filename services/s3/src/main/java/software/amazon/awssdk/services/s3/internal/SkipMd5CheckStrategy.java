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

import static software.amazon.awssdk.http.Headers.CONTENT_MD5;
import static software.amazon.awssdk.http.Headers.CONTENT_TYPE;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.util.Mimetypes;
import software.amazon.awssdk.http.Headers;
import software.amazon.awssdk.http.SdkHttpHeaders;
import software.amazon.awssdk.services.s3.S3AdvancedConfiguration;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleRequest;
import software.amazon.awssdk.services.s3.model.PutBucketReplicationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketTaggingRequest;
import software.amazon.awssdk.services.s3.model.PutObjectAclRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

/**
 * Logic for determining whether MD5 checksum validation should be performed or not.
 */
public class SkipMd5CheckStrategy {

    private static final Set<Class> MD5_REQUIRED_REQUESTS = new HashSet<>(
        Arrays.asList(PutBucketCorsRequest.class, PutBucketLifecycleConfigurationRequest.class, PutBucketLifecycleRequest.class,
                      PutBucketTaggingRequest.class, PutBucketReplicationRequest.class, DeleteObjectsRequest.class));

    private static final Set<Class> MD5_COMPUTABLE_REQUESTS = new HashSet<>(
        Arrays.asList(PutObjectRequest.class, UploadPartRequest.class, PutObjectAclRequest.class));

    private static final Set<Class> CLIENT_SIDE_VALIDATION_REQUESTS = new HashSet<>(
        Arrays.asList(GetObjectRequest.class, PutObjectRequest.class, UploadPartRequest.class));

    private SkipMd5CheckStrategy() {
    }

    /**
     * Based on the given {@link SdkRequest}, returns whether the specified request should
     * skip MD5 check on the requested object content. Specifically, MD5 check should be skipped if
     * one of the following conditions are true:
     * <ol>
     * <li>The {@link S3AdvancedConfiguration#clientSideMd5ValidationEnabled()} is set to false</li>
     * <li>The request is not in {@link #CLIENT_SIDE_VALIDATION_REQUESTS}</li>
     * <li>The request is a {@link GetObjectRequest} that involves range-get operation or SSE-C</li>
     * <li>The request is a {@link PutObjectRequest} that involves SSE-C or SSE-KMS and {@link PutObjectRequest#contentMD5()} is not set</li>
     * <li>The request is a {@link UploadPartRequest} that involves SSE-C and {@link UploadPartRequest#contentMD5()} is not set</li>
     * </ol>
     *
     * @return True if client side validation should be skipped, false otherwise.
     */
    public static boolean skipClientSideValidationPerRequest(S3AdvancedConfiguration advancedConfiguration, SdkRequest request) {
        if (!CLIENT_SIDE_VALIDATION_REQUESTS.contains(request.getClass()) || isClientSideValidationDisabledByConfig(advancedConfiguration)) {
            return true;
        }

        if (request instanceof PutObjectRequest) {
            PutObjectRequest putObjectRequest = (PutObjectRequest) request;
            return containsNonNull(putObjectRequest.sseCustomerKey(), putObjectRequest.ssekmsKeyId(), putObjectRequest.contentMD5());
        }

        if (request instanceof UploadPartRequest) {
            UploadPartRequest uploadPartRequest = (UploadPartRequest) request;
            return containsNonNull(uploadPartRequest.sseCustomerKey(), uploadPartRequest.contentMD5());
        }

        return false;
    }

    /**
     * Determines whether to do client side validation on both the request and the response.
     *
     * {@link #skipClientSideValidationPerRequest} and
     * {@link #skipClientSideValidationPerPutResponse} for more details on the
     * criterion.
     *
     * @return True if client side validation should be skipped, false otherwise.
     */
    public static boolean skipClientSideValidation(S3AdvancedConfiguration advancedConfiguration, SdkRequest sdkRequest, SdkHttpHeaders responseHeaders) {

        if (isClientSideValidationDisabledByConfig(advancedConfiguration)) {
            return true;
        }

        if (sdkRequest instanceof GetObjectRequest) {
            return skipClientSideValidationPerGetResponse(responseHeaders);
        }

        return skipClientSideValidationPerRequest(advancedConfiguration, sdkRequest) || skipClientSideValidationPerPutResponse(responseHeaders);
    }

    /**
     * Determines whether the client should calculate and send the {@link Headers#CONTENT_MD5}
     * header to be validated by S3 per the request.
     *
     * <p>
     * Currently we always try and do server side validation for non-streaming PUT/POST operations unless any of the following
     * conditions are true:
     *
     * <ol>
     * <li>it's been explicitly disabled by {@link S3AdvancedConfiguration#serverSideMd5ValidationEnabled} </li>
     * <li>the request is streaming operations {@link #isStreamingOperation(SdkHttpHeaders)}</li>
     * <li>the request has {@link Headers#CONTENT_MD5} set</li>
     * </ol>
     * @see <a href="http://docs.aws.amazon.com/AmazonS3/latest/API/RESTCommonResponseHeaders.html">ETag</a>
     *
     * @return True if server side validation should be skipped, false otherwise.
     */
    public static boolean skipServerSideValidation(SdkHttpHeaders headers, S3AdvancedConfiguration advancedConfiguration, SdkRequest request) {

        if (MD5_REQUIRED_REQUESTS.contains(request.getClass())) {
            return false;
        }

        return isServerSideValidationDisabledByConfig(advancedConfiguration)
               || isStreamingOperation(headers)
               || !MD5_COMPUTABLE_REQUESTS.contains(request.getClass())
               || headers.firstMatchingHeader(CONTENT_MD5).isPresent();
    }

    /**
     * Determines whether the client should use the <code>ETag</code> header returned by S3 to
     * validate the integrity of the message client side. We skip the client side check if any of
     * the following conditions are true:
     * <ol>
     * <li><code>ETag</code> does not exist</li>
     * </ol>
     *
     * @return True if client side validation should be skipped, false otherwise.
     */
    private static boolean skipClientSideValidationPerPutResponse(SdkHttpHeaders response) {
        return !response.firstMatchingHeader("ETag").isPresent();
    }

    private static boolean skipClientSideValidationPerGetResponse(SdkHttpHeaders response) {
        return !response.firstMatchingHeader("x-amz-transfer-encoding").filter("append-md5"::equals).isPresent();
    }

    private static boolean isClientSideValidationDisabledByConfig(S3AdvancedConfiguration advancedConfiguration) {
        return advancedConfiguration != null && !advancedConfiguration.clientSideMd5ValidationEnabled();
    }

    private static boolean isServerSideValidationDisabledByConfig(S3AdvancedConfiguration advancedConfiguration) {
        return advancedConfiguration != null && !advancedConfiguration.serverSideMd5ValidationEnabled();
    }

    private static boolean isStreamingOperation(SdkHttpHeaders request) {
        return request.firstMatchingHeader(CONTENT_TYPE).filter(c -> c.equals(Mimetypes.MIMETYPE_OCTET_STREAM)).isPresent();

    }

    /**
     * Helper method to avoid long chains of non null checks.
     *
     * @param items items to check
     * @return True if any of the provided items is not null. False if all items are null.
     */
    private static boolean containsNonNull(Object... items) {
        return Stream.of(items).anyMatch(item -> !Objects.isNull(item));
    }
}
