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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.util.Md5Utils;
import software.amazon.awssdk.core.util.StringInputStream;
import software.amazon.awssdk.testutils.RandomTempFile;
import software.amazon.awssdk.utils.IoUtils;

public class ChecksumValidationInputStreamTest {

    private static final int FILE_LENGTH = 5000;
    private static final int CONTENT_LENGTH = 4084;
    private static String content;
    private static File file;

    @BeforeClass
    public static void setup() throws IOException {
        file = new RandomTempFile("test", FILE_LENGTH);
        content = RandomStringUtils.random(FILE_LENGTH);
    }

    @AfterClass
    public static void cleanup() {
        file.delete();
    }

    @Test(expected = SdkClientException.class)
    public void extractedHashNotMatch_ShouldThrowException() throws Exception {

        InputStream inputStream = new FileInputStream(file);
        ChecksumValidationInputStream validationInputStream = new ChecksumValidationInputStream(inputStream, CONTENT_LENGTH);

        IoUtils.drainInputStream(validationInputStream);
    }

    @Test
    public void contentWithTrailingChecksum_ShouldNotThrowException() throws Exception {

        InputStream inputStream = new ByteArrayInputStream(appendTrailingChecksum());
        ChecksumValidationInputStream validationInputStream = new ChecksumValidationInputStream(inputStream, CONTENT_LENGTH + 10);

        IoUtils.drainInputStream(validationInputStream);
        assertThat(validationInputStream.read()).isEqualTo(-1);
    }

    private byte[] appendTrailingChecksum() throws IOException {
        byte[] md5 = Md5Utils.computeMD5Hash(new StringInputStream(content));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(content.getBytes());
        outputStream.write(md5);
        return outputStream.toByteArray();
    }
}
