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

package software.amazon.awssdk.services.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.awssdk.testutils.service.S3BucketUtils.temporaryBucketName;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseBytes;
import software.amazon.awssdk.core.sync.StreamingResponseHandler;
import software.amazon.awssdk.core.util.Md5Utils;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutBucketCorsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.testutils.RandomTempFile;
import software.amazon.awssdk.utils.Base64Utils;

public class Md5ValidationIntegrationTest extends S3IntegrationTestBase {

    private static final String BUCKET = temporaryBucketName(Md5ValidationIntegrationTest.class);
    private static final String KEY = "test";
    private final GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                                                      .bucket(BUCKET)
                                                                      .key(KEY)
                                                                      .build();

    private static S3Client s3ClientSideValidationDisabled;
    private static File file;

    @BeforeClass
    public static void setup() throws Exception {
        createBucket(BUCKET);
        file = new RandomTempFile("test", 600);
        s3.putObject(PutObjectRequest.builder()
                                     .bucket(BUCKET)
                                     .key(KEY)
                                     .build(), file.toPath());

        s3ClientSideValidationDisabled =
            s3ClientBuilder().advancedConfiguration(b -> b.clientSideMd5ValidationEnabled(false)).build();
    }

    @AfterClass
    public static void tearDownFixture() {
        deleteBucketAndAllContents(BUCKET);
        file.delete();
    }

    @Test
    public void uploadPart_ShouldAddContentMD5IfNotPresent() {
        PutBucketCorsResponse putBucketCorsResponse = s3.putBucketCors(b -> b.bucket(BUCKET).corsConfiguration(c -> c.corsRules(
            CORSRule.builder().maxAgeSeconds(1000).allowedMethods("DELETE", "PUT", "GET").allowedOrigins("*").build())));
        assertThat(putBucketCorsResponse).isNotNull();
    }

    @Test(expected = S3Exception.class)
    public void putObject_RequestHasIncorrectContentMD5_ShouldNotOverride() {
        s3.putObject(PutObjectRequest.builder().bucket(BUCKET).key(KEY).contentMD5("balhblah").build(), RequestBody.of("test"));
    }

    @Test
    public void putBucektCors_RequestHasInvalidContentMD5_ShouldOverrideContentMD5() {
        PutBucketCorsResponse putBucketCorsResponse = s3.putBucketCors(b -> b.bucket(BUCKET).contentMD5("blahblah").corsConfiguration(c -> c.corsRules(
            CORSRule.builder().maxAgeSeconds(1000).allowedMethods("DELETE", "PUT", "GET").allowedOrigins("*").build())));
        assertThat(putBucketCorsResponse).isNotNull();
    }

    @Test
    public void getObject_NormalRequest_ShouldAppendTrailingChecksum() {
        final ResponseBytes<GetObjectResponse> object =
            s3.getObject(getObjectRequest, StreamingResponseHandler.toBytes());

        // contentLength should be 100 + 16 (Hashlength) = 116
        assertThat(object.response().contentLength()).isEqualTo(616L);
    }

    @Test
    public void getObject_InvolvesSsec_ShouldAppendTrailingChecksum() throws IOException {

        String sseK = getKey();
        String key = "test_ssec";
        byte[] encryptionKey = Base64Utils.decode(sseK);
        final String md5 = Md5Utils.md5AsBase64(encryptionKey);

        s3.putObject(PutObjectRequest.builder().bucket(BUCKET).key(key).sseCustomerKey(sseK).sseCustomerAlgorithm("AES256").sseCustomerKeyMD5(md5).build(), RequestBody.of(file));

        ResponseBytes<GetObjectResponse> responseBytes = s3.getObject(getObjectRequest.toBuilder().key(key).sseCustomerKey(sseK).sseCustomerAlgorithm("AES256").build(), StreamingResponseHandler.toBytes());

        assertThat(responseBytes.response().contentLength()).isEqualTo(616L);
    }

    @Test
    public void getObjectRange_NormalRequest_ShouldAppendTrailingChecksum() {
        final ResponseBytes<GetObjectResponse> object =
            s3.getObject(getObjectRequest.toBuilder().range("bytes=0-89").build(), StreamingResponseHandler.toBytes());

        // contentLength should be 90 + 16 (Hashlength) = 106
        assertThat(object.response().contentLength()).isEqualTo(106L);
    }

    @Test
    public void getObjectRange_ClientSideValidationDisabled_ShouldNotAppendTrailingChecksum() {
        final ResponseBytes<GetObjectResponse> object =
            s3ClientSideValidationDisabled.getObject(getObjectRequest.toBuilder().build(), StreamingResponseHandler.toBytes());

        // contentLength should be 90 + 16 (Hashlength) = 106
        assertThat(object.response().contentLength()).isEqualTo(600L);
    }

    private String getKey() {
        return Base64Utils.encodeAsString(generateSecretKey().getEncoded());
    }

    private SecretKey generateSecretKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256, new SecureRandom());
            return generator.generateKey();
        } catch (Exception e) {
           throw new AssertionError("failed to generate secretKey", e);
        }
    }
}
