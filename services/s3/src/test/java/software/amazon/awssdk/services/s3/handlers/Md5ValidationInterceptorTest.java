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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static software.amazon.awssdk.http.Headers.CONTENT_LENGTH;
import static software.amazon.awssdk.http.Headers.CONTENT_MD5;
import static software.amazon.awssdk.http.Headers.CONTENT_TYPE;
import static utils.S3MockUtils.newPutObjectRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.interceptor.AwsExecutionAttributes;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.util.Mimetypes;
import software.amazon.awssdk.core.util.StringInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.services.s3.internal.Md5DigestCalculatingInputStream;
import software.amazon.awssdk.services.s3.S3AdvancedConfiguration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.utils.BinaryUtils;
import software.amazon.awssdk.utils.IoUtils;


public class Md5ValidationInterceptorTest {

    private Md5ValidationInterceptor interceptor;

    @Before
    public void setup() {
        interceptor = new Md5ValidationInterceptor();
    }

    @Test
    public void putObject_StreamingRequest_ShouldDoClientSideValidation() {

        SdkHttpFullRequest sdkHttpFullRequest = sdkHttpFullRequest(Mimetypes.MIMETYPE_OCTET_STREAM);
        PutObjectRequest putObjectRequest = newPutObjectRequest();

        ExecutionAttributes executionAttributes = executionAttributes();
        SdkHttpFullRequest modifiedRequest = interceptor.modifyHttpRequest(context(sdkHttpFullRequest, putObjectRequest), executionAttributes);
        assertThat(executionAttributes.getAttribute(AwsExecutionAttributes.MESSAGE_DIGEST)).isNotNull();
        assertThat(modifiedRequest.content().get()).isInstanceOf(Md5DigestCalculatingInputStream.class);
    }

    @Test
    public void putObject_StreamingRequest_ValidationDisabled_ShouldNotModifyRequest() {

        SdkHttpFullRequest sdkHttpFullRequest = sdkHttpFullRequest(Mimetypes.MIMETYPE_OCTET_STREAM);
        PutObjectRequest putObjectRequest = newPutObjectRequest();

        ExecutionAttributes executionAttributes = executionAttributes().putAttribute(AwsExecutionAttributes.SERVICE_ADVANCED_CONFIG,
                                                                                     S3AdvancedConfiguration.builder().clientSideMd5ValidationEnabled(false).build());
        SdkHttpFullRequest modifiedRequest = interceptor.modifyHttpRequest(context(sdkHttpFullRequest, putObjectRequest), executionAttributes);
        assertThat(modifiedRequest).isEqualTo(sdkHttpFullRequest);
    }

    @Test
    public void putObject_SkipClientValidationTrue_ShouldNotModifyRequest() {

        SdkHttpFullRequest sdkHttpFullRequest = sdkHttpFullRequest(Mimetypes.MIMETYPE_OCTET_STREAM);
        PutObjectRequest putObjectRequest = newPutObjectRequest();

        ExecutionAttributes executionAttributes = executionAttributes().putAttribute(AwsExecutionAttributes.SERVICE_ADVANCED_CONFIG,
                                                                                     S3AdvancedConfiguration.builder().clientSideMd5ValidationEnabled(false).build());
        SdkHttpFullRequest modifiedRequest = interceptor.modifyHttpRequest(context(sdkHttpFullRequest, putObjectRequest), executionAttributes);
        assertThat(modifiedRequest).isEqualTo(sdkHttpFullRequest);
    }

    @Test
    public void md5RequiredOperations_normalRequest_ShouldAddContentMd5() {
        PutBucketCorsRequest putBucketCorsRequest = PutBucketCorsRequest.builder().build();

        Context.ModifyHttpRequest context = context(putBucketCorsRequest);
        SdkHttpFullRequest modifiedRequest = interceptor.modifyHttpRequest(context, executionAttributes());
        assertThat(modifiedRequest).isNotEqualTo(context.httpRequest());
        assertThat(modifiedRequest.headers().containsKey(CONTENT_MD5)).isTrue();
    }

    @Test
    public void putObject_SkipServerSideValidationTrue_ShouldClientValidation() {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder().build();
        ExecutionAttributes attributes = executionAttributes()
            .putAttribute(AwsExecutionAttributes.SERVICE_ADVANCED_CONFIG,
                          S3AdvancedConfiguration.builder().serverSideMd5ValidationEnabled(false).build());

        Context.ModifyHttpRequest context = context(putObjectRequest);
        SdkHttpFullRequest modifiedRequest = interceptor.modifyHttpRequest(context, attributes);
        assertThat(modifiedRequest).isNotEqualTo(context.httpRequest());
    }

    @Test
    public void putObject_SkipClientSideValidationTrue_ShouldNotModifyResponse() {
        PutObjectRequest putObjectRequest = newPutObjectRequest();
        ExecutionAttributes attributes = executionAttributes()
            .putAttribute(AwsExecutionAttributes.SERVICE_ADVANCED_CONFIG,
                          S3AdvancedConfiguration.builder().clientSideMd5ValidationEnabled(false).build());

        Context.ModifyHttpResponse context = context(sdkHttpFullResponse(), putObjectRequest);

        SdkHttpFullResponse modifiedResponse = interceptor.modifyHttpResponse(context, attributes);

        assertThat(modifiedResponse).isEqualTo(context.httpResponse());
    }

    @Test
    public void putObject_NoEtag_ShouldNotModifyResponse() {
        PutObjectRequest putObjectRequest = newPutObjectRequest();
        ExecutionAttributes attributes = executionAttributes();
        Context.ModifyHttpResponse context = context(sdkHttpFullResponse(), putObjectRequest);

        SdkHttpFullResponse modifiedResponse = interceptor.modifyHttpResponse(context, attributes);

        assertThat(modifiedResponse).isEqualTo(context.httpResponse());
    }

    @Test(expected = SdkClientException.class)
    public void putObject_NormalRequest_Md5Mismatch_ShouldThrowException() throws NoSuchAlgorithmException {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder().build();
        String etag = "1b2cf535f27731c974343645a3985328";
        ExecutionAttributes attributes = executionAttributes()
            .putAttribute(AwsExecutionAttributes.MESSAGE_DIGEST, MessageDigest.getInstance("MD5"));
        SdkHttpFullResponse sdkHttpFullResponse = sdkHttpFullResponse().toBuilder()
            .header("Etag", etag).build();
        Context.ModifyHttpResponse context = context(sdkHttpFullResponse, putObjectRequest);

        interceptor.modifyHttpResponse(context, attributes);
    }

    @Test
    public void putObject_NormalRequest_Md5Match() throws NoSuchAlgorithmException {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder().build();
        String etag = "1b2cf535f27731c974343645a3985328";
        byte[] expectedMD5 = BinaryUtils.fromHex(etag);
        MessageDigest mockMessageDigest = mock(MessageDigest.class);
        when(mockMessageDigest.digest()).thenReturn(expectedMD5);

        ExecutionAttributes attributes = executionAttributes()
            .putAttribute(AwsExecutionAttributes.MESSAGE_DIGEST, mockMessageDigest);
        SdkHttpFullResponse sdkHttpFullResponse = sdkHttpFullResponse().toBuilder()
                                                                       .header("ETag", etag).build();
        Context.ModifyHttpResponse context = context(sdkHttpFullResponse, putObjectRequest);

        SdkHttpFullResponse modifyHttpResponse = interceptor.modifyHttpResponse(context, attributes);
        assertThat(modifyHttpResponse).isEqualTo(context.httpResponse());
    }

    @Test
    public void getObject_SkipClientSideValidation_ShouldNotModifyResponse() {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().build();
        ExecutionAttributes attributes = executionAttributes()
            .putAttribute(AwsExecutionAttributes.SERVICE_ADVANCED_CONFIG,
                          S3AdvancedConfiguration.builder().clientSideMd5ValidationEnabled(false).build());

        Context.ModifyHttpResponse context = context(sdkHttpFullResponse(), getObjectRequest);

        SdkHttpFullResponse modifiedResponse = interceptor.modifyHttpResponse(context, attributes);

        assertThat(modifiedResponse).isEqualTo(context.httpResponse());
    }

    @Test
    public void getObject_NoETag_ShouldNotModifyResponse() {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().build();
        ExecutionAttributes attributes = executionAttributes();
        Context.ModifyHttpResponse context = context(sdkHttpFullResponse(), getObjectRequest);

        SdkHttpFullResponse modifiedResponse = interceptor.modifyHttpResponse(context, attributes);

        assertThat(modifiedResponse).isEqualTo(context.httpResponse());
    }

    @Test
    public void getObject_NormalRequest_ShouldModifyResponse() {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().build();
        ExecutionAttributes attributes = executionAttributes();
        SdkHttpFullResponse sdkHttpFullResponse = sdkHttpFullResponse().toBuilder()
                                                                       .header("x-amz-transfer-encoding", "append-md5")
                                                                       .header(CONTENT_LENGTH, "26").build();
        Context.ModifyHttpResponse context = context(sdkHttpFullResponse, getObjectRequest);
        SdkHttpFullResponse modifiedResponse = interceptor.modifyHttpResponse(context, attributes);

        assertThat(modifiedResponse).isNotEqualTo(context.httpResponse());
        IoUtils.drainInputStream(modifiedResponse.content().get());
    }

    private ExecutionAttributes executionAttributes() {
        return new ExecutionAttributes();
    }

    private SdkHttpFullRequest sdkHttpFullRequest(String contentType) {
        return SdkHttpFullRequest.builder()
                                 .protocol("http")
                                 .host("test.com")
                                 .port(80)
                                 .header(CONTENT_TYPE, contentType)
                                 .content(new StringInputStream("helloworld"))
                                 .method(SdkHttpMethod.GET)
                                 .build();
    }

    private SdkHttpFullRequest sdkHttpFullRequest() {
        return sdkHttpFullRequest(Mimetypes.MIMETYPE_XML);
    }

    private Context.ModifyHttpRequest context(SdkHttpFullRequest sdkHttpFullRequest, SdkRequest sdkRequest) {
        return new Context.ModifyHttpRequest() {

            @Override
            public SdkHttpFullRequest httpRequest() {
                return sdkHttpFullRequest;
            }

            @Override
            public SdkRequest request() {
                return sdkRequest;
            }
        };
    }

    private SdkHttpFullResponse sdkHttpFullResponse() {
        return SdkHttpFullResponse.builder()
                                  .statusCode(200)
                                  .content(new AbortableInputStream(new StringInputStream("TEST"), () -> {}))
                                  .build();
    }

    private Context.ModifyHttpResponse context(SdkHttpFullResponse sdkHttpFullResponse, SdkRequest sdkRequest) {
        return new Context.ModifyHttpResponse() {

            @Override
            public SdkHttpFullResponse httpResponse() {
                return sdkHttpFullResponse;
            }

            @Override
            public SdkHttpFullRequest httpRequest() {
                return sdkHttpFullRequest();
            }

            @Override
            public SdkRequest request() {
                return sdkRequest;
            }
        };
    }

    private Context.ModifyHttpRequest context(SdkRequest sdkRequest) {
        return context(sdkHttpFullRequest(), sdkRequest);
    }
}
