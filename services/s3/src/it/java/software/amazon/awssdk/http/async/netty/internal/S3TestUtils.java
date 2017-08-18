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

package software.amazon.awssdk.http.async.netty.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Random;
import software.amazon.awssdk.utils.BinaryUtils;

public class S3TestUtils {

    private static final int ASCII_LOW = 33; // '!', skipping space for readability
    private static final int ASCII_HIGH = 126;
    // include a line break character
    private static final int modulo = ASCII_HIGH - ASCII_LOW + 2;

    private static final Random RAND = new Random();

    public static FileAndMd5 generateRandomAsciiFile(long byteSize) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        File file = File.createTempFile("CryptoTestUtils", ".txt");
        file.deleteOnExit();
        int BUFSIZE = 1024 * 8;
        byte[] buf = new byte[1024 * 8];
        long counts = byteSize / BUFSIZE;
        try (OutputStream out = new FileOutputStream(file)) {
            while (counts-- > 0) {
                final byte[] toWrite = fillRandomAscii(buf);
                out.write(toWrite);
                md.update(toWrite);
            }
            int remainder = (int) byteSize % BUFSIZE;
            if (remainder > 0) {
                final byte[] toWrite = fillRandomAscii(new byte[remainder]);
                out.write(toWrite);
                md.update(toWrite);
            }
        }
        return new FileAndMd5(file, md.digest());
    }

    private static byte[] fillRandomAscii(byte[] bytes) {
        RAND.nextBytes(bytes);
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if (b < ASCII_LOW || b > ASCII_HIGH) {
                byte c = (byte) (b % modulo);
                if (c < 0) {
                    c = (byte) (c + modulo);
                }
                bytes[i] = (byte) (c + ASCII_LOW);
                if (bytes[i] > ASCII_HIGH) {
                    bytes[i] = (byte) '\n';
                }
            }
        }
        return bytes;
    }

    public static class FileAndMd5 {
        private final File file;
        private final byte[] md5Checksum;

        private FileAndMd5(File file, byte[] md5Checksum) {
            this.file = file;
            this.md5Checksum = md5Checksum;
        }

        public File file() {
            return file;
        }

        public String base64EncodedChecksum() {
            return BinaryUtils.toBase64(md5Checksum);
        }
    }
}
