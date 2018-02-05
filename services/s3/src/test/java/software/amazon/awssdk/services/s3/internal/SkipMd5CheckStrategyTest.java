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


import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.awssdk.http.Headers.CONTENT_MD5;
import static software.amazon.awssdk.http.Headers.CONTENT_TYPE;
import static software.amazon.awssdk.services.s3.internal.SkipMd5CheckStrategy.skipClientSideValidation;
import static utils.S3MockUtils.newGetObjectRequest;
import static utils.S3MockUtils.newPutObjectRequest;
import static utils.S3MockUtils.newUploadPartRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.util.Mimetypes;
import software.amazon.awssdk.http.SdkHttpHeaders;
import software.amazon.awssdk.services.s3.S3AdvancedConfiguration;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleRequest;
import software.amazon.awssdk.services.s3.model.PutBucketReplicationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketTaggingRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

public class SkipMd5CheckStrategyTest {

    private Map<String, List<String>> headers;

    @Before
    public void setup() {
        headers = new HashMap<>();
        headers.put(CONTENT_TYPE, Collections.singletonList(Mimetypes.MIMETYPE_XML));
    }

    @Test
    public void putObject_StreamingRequest_ShouldSkipServerSideValidation() {
        PutObjectRequest request = PutObjectRequest.builder().build();
        headers.put(CONTENT_TYPE, Collections.singletonList(Mimetypes.MIMETYPE_OCTET_STREAM));
        assertThat(SkipMd5CheckStrategy.skipServerSideValidation(header(), null, request)).isTrue();
    }

    @Test
    public void putObject_NonStreamingRequest_ShouldNotSkipServerSideValidation() {
        PutObjectRequest request = PutObjectRequest.builder().build();
        SdkHttpHeaders sdkHttpHeaders = () -> headers;
        assertThat(SkipMd5CheckStrategy.skipServerSideValidation(sdkHttpHeaders, null, request)).isFalse();
    }

    @Test
    public void putObject_NonStreamingRequestValidationDisabled_ShouldSkipServerSideValidation() {
        PutObjectRequest request = PutObjectRequest.builder().build();
        assertThat(SkipMd5CheckStrategy.skipServerSideValidation(md5Header(), serverSideValidationDisabled(), request)).isTrue();
    }

    @Test
    public void putObject_HasContentMD5Header_ShouldSkipServerSideValidation() {
        PutObjectRequest request = PutObjectRequest.builder().build();
        assertThat(SkipMd5CheckStrategy.skipServerSideValidation(md5Header(), null, request)).isTrue();
    }

    @Test
    public void md5RequiredOperations_ShouldNotSkipServerSideValidation() {
        md5RequiredRequests().forEach(request ->
                                          assertThat(SkipMd5CheckStrategy.skipServerSideValidation(header(), null, request)).isFalse());
    }

    @Test
    public void md5RequiredOperations_HasContentMD5Header_ShouldNotSkipServerSideValidation() {
        md5RequiredRequests().forEach(request ->
                                          assertThat(SkipMd5CheckStrategy.skipServerSideValidation(md5Header(), null, request)).isFalse());
    }

    @Test
    public void md5RequiredOperations_ServerSideValidationDisabled_ShouldNotSkipServerSideValidation() {
        md5RequiredRequests().forEach(request ->
                                          assertThat(SkipMd5CheckStrategy.skipServerSideValidation(header(), serverSideValidationDisabled(), request)).isFalse());
    }

    @Test
    public void nonMD5ComputableOperations_ShouldSkipServerSideValidation() {
        HeadObjectRequest request = HeadObjectRequest.builder().build();
        assertThat(SkipMd5CheckStrategy.skipServerSideValidation(header(), null, request)).isTrue();
    }

    @Test
    public void getObject_NormalRequest_ShouldNotSkipClientSideValidation() {
        GetObjectRequest request = newGetObjectRequest();
        assertThat(skipClientSideValidation(null, request, trailingChecksumHeader())).isFalse();
    }

    @Test
    public void getObject_RangeRequest_ShouldNotSkipClientSideValidation() {
        GetObjectRequest request = newGetObjectRequest().toBuilder().range("range").build();
        assertThat(skipClientSideValidation(null, request, trailingChecksumHeader())).isFalse();
    }

    @Test
    public void getObject_InvolvesSseC_ShouldNotSkipClientSideValidation() {
        GetObjectRequest request = newGetObjectRequest().toBuilder().sseCustomerKey("key").build();
        assertThat(skipClientSideValidation(null, request, trailingChecksumHeader())).isFalse();
    }

    @Test
    public void getObject_NoTrailingChecksumInResponse_ShouldSkipClientSideValidation() {
        GetObjectRequest request = newGetObjectRequest();
        assertThat(skipClientSideValidation(null, request, header())).isTrue();
    }

    @Test
    public void getObject_ValidationDisabled_ShouldSkipClientSideValidation() {
        GetObjectRequest request = newGetObjectRequest();
        assertThat(skipClientSideValidation(clientSideValidationDisabled(), request, header())).isTrue();
    }

    @Test
    public void putObject_NormalRequest_ShouldNotSkipClientSideValidation() {
        PutObjectRequest request = newPutObjectRequest();
        assertThat(skipClientSideValidation(null, request, etagHeader())).isFalse();
    }

    @Test
    public void putObject_InvolvesSseC_ShouldSkipClientSideValidation() {
        PutObjectRequest request = newPutObjectRequest().toBuilder().sseCustomerKey("test").build();
        assertThat(skipClientSideValidation(null, request, etagHeader())).isTrue();
    }

    @Test
    public void putObject_InvolvesSseK_ShouldSkipClientSideValidation() {
        PutObjectRequest request = newPutObjectRequest().toBuilder().ssekmsKeyId("test").build();
        assertThat(skipClientSideValidation(null, request, etagHeader())).isTrue();
    }

    @Test
    public void putObject_ValidationDisabled_ShouldSkipClientSideValidation() {
        PutObjectRequest request = newPutObjectRequest();
        assertThat(skipClientSideValidation(clientSideValidationDisabled(), request, etagHeader())).isTrue();
    }

    @Test
    public void putObject_NoETagInResponse_ShouldSkipClientSideValidation() {
        PutObjectRequest request = newPutObjectRequest();
        assertThat(skipClientSideValidation(null, request, header())).isTrue();
    }

    @Test
    public void uploadObject_ValidationDisabled_ShouldSkipClientSideValidation() {
        UploadPartRequest request = newUploadPartRequest();
        assertThat(skipClientSideValidation(clientSideValidationDisabled(), request, etagHeader())).isTrue();
    }

    @Test
    public void uploadObject_InvolvesSseC_ShouldSkipClientSideValidation() {
        PutObjectRequest request = newPutObjectRequest().toBuilder().sseCustomerKey("test").build();
        assertThat(skipClientSideValidation(null, request, etagHeader())).isTrue();
    }

    @Test
    public void uploadObject_NormalRequest_ShouldNotSkipClientSideValidation() {
        UploadPartRequest request = newUploadPartRequest();
        assertThat(skipClientSideValidation(null, request, etagHeader())).isFalse();
    }

    @Test
    public void uploadObject_HasContentMD5_ShouldSkipClientSideValidation() {
        UploadPartRequest request = newUploadPartRequest().toBuilder().contentMD5("somecontentmd5").build();
        assertThat(skipClientSideValidation(null, request, etagHeader())).isTrue();
    }

    private List<SdkRequest> md5RequiredRequests() {
        return Arrays.asList(
            PutBucketCorsRequest.builder().build(),
            PutBucketLifecycleRequest.builder().build(),
            PutBucketLifecycleConfigurationRequest.builder().build(),
            PutBucketTaggingRequest.builder().build(),
            PutBucketReplicationRequest.builder().build(),
            DeleteObjectsRequest.builder().build()
        );
    }

    private SdkHttpHeaders header() {
        return () -> headers;
    }

    private SdkHttpHeaders md5Header() {
        headers.put(CONTENT_MD5, Collections.singletonList("hash"));
        return () -> headers;
    }

    private SdkHttpHeaders etagHeader() {
        headers.put("ETag", Collections.singletonList("hash"));
        return () -> headers;
    }

    private SdkHttpHeaders trailingChecksumHeader() {
        headers.put("x-amz-transfer-encoding", Collections.singletonList("append-md5"));
        return () -> headers;
    }

    private S3AdvancedConfiguration serverSideValidationDisabled() {
        return S3AdvancedConfiguration.builder().serverSideMd5ValidationEnabled(false).build();
    }

    private S3AdvancedConfiguration clientSideValidationDisabled() {
        return S3AdvancedConfiguration.builder().clientSideMd5ValidationEnabled(false).build();
    }
}
