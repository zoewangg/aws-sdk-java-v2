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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.sync.ResponseInputStream;
import software.amazon.awssdk.sync.StreamingResponseHandler;
import software.amazon.awssdk.utils.BinaryUtils;
import software.amazon.awssdk.utils.IoUtils;

public class GetObjectIntegrationTest {

    private S3Client s3;

    private final GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                                                      .bucket("shorea-public")
                                                                      .key("sdk.jar")
                                                                      .build();

    @Before
    public void setup() {
        s3 = S3Client.builder()
                     .region(Region.US_EAST_1)
                     .build();
    }

    @Test
    public void toInputStream() throws Exception {
        try (ResponseInputStream<GetObjectResponse> content =
                     s3.getObject(getObjectRequest, StreamingResponseHandler.toInputStream())) {
            assertMd5MatchesEtag(content, content.response());
        }
    }

    @Test
    public void toFile() throws Exception {
        Path path = Paths.get("/var/tmp/resp.out");
        GetObjectResponse response = s3.getObject(getObjectRequest, StreamingResponseHandler.toFile(path));
        assertMd5MatchesEtag(new FileInputStream(path.toFile()), response);
    }

    @Test
    public void toOutputStream() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GetObjectResponse response = s3.getObject(getObjectRequest, StreamingResponseHandler.toOutputStream(baos));
        assertMd5MatchesEtag(new ByteArrayInputStream(baos.toByteArray()), response);
    }

    private void assertMd5MatchesEtag(InputStream content, GetObjectResponse response) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new DigestInputStream(content, md)) {
            IoUtils.drainInputStream(is);
        }
        byte[] expectedMd5 = BinaryUtils.fromHex(response.eTag().replace("\"", ""));
        byte[] calculatedMd5 = md.digest();
        if (!Arrays.equals(expectedMd5, calculatedMd5)) {
            throw new AssertionError(
                    String.format("Content malformed. Expected checksum was %s but calculated checksum was %s",
                                  BinaryUtils.toBase64(expectedMd5), BinaryUtils.toBase64(calculatedMd5)));
        }
    }
}
