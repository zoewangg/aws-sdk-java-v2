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

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.runtime.io.SdkDigestInputStream;
import software.amazon.awssdk.core.runtime.io.SdkFilterInputStream;
import software.amazon.awssdk.utils.Base64Utils;
import software.amazon.awssdk.utils.BinaryUtils;


/**
 * Simple InputStream wrapper that examines the wrapped stream's contents as
 * they are read and calculates MD5 digest and compared it against the extracted trailing MD5 digest.
 */
public class ChecksumValidationInputStream extends SdkDigestInputStream {

    private static final int HASH_LENGTH = 16;

    private final int contentLength;

    public ChecksumValidationInputStream(InputStream in, int contentLength) throws NoSuchAlgorithmException {
        super(in, MessageDigest.getInstance("MD5"));
        this.contentLength = contentLength;
    }

    @Override
    public int read() throws IOException {
        int ch = super.read();

        if (available() == HASH_LENGTH) {
            validateChecksum();
        }
        return ch;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {

        if (len <= contentLength - HASH_LENGTH) {
            return super.read(b, off, len);
        }

        int result = super.read(b, off, contentLength - HASH_LENGTH);
        validateChecksum();
        return result;
    }

    private void validateChecksum() throws IOException {
        byte[] serverChecksum = new byte[HASH_LENGTH];
        byte[] contentHash = getMD5Checksum();

        int result = super.read(serverChecksum, 0, HASH_LENGTH);

        if (result == -1) {
            return;
        }
        //
        String contentMd5 = Base64Utils.encodeAsString(contentHash);
        String serverMd5 = Base64Utils.encodeAsString(serverChecksum);

        System.out.println("contentMD5: " + contentMd5 + " lenght: " + contentMd5.length());
        System.out.println("serverMd5: " + serverMd5);

        System.out.println("contentMD5: " + BinaryUtils.toHex(contentHash));
        System.out.println("serverMd5: " + BinaryUtils.toHex(serverChecksum));

        if (!Arrays.equals(serverChecksum, contentHash)) {
            throw new SdkClientException(String.format("Unable to verify integrity of data download.  "
                                                       + "Client calculated content hash: %s didn't match hash calculated by Amazon S3: %s "
                                                       + "The data may be corrupt.", contentMd5, serverMd5));
        }
    }

    private byte[] getMD5Checksum() {
        return digest.digest();
    }
}
