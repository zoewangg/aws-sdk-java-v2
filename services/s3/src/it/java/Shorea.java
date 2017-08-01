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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import software.amazon.awssdk.async.AsyncResponseHandler;
import software.amazon.awssdk.auth.ProfileCredentialsProvider;
import software.amazon.awssdk.client.builder.ClientAsyncHttpConfiguration;
import software.amazon.awssdk.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.nio.netty.DefaultEventLoopGroupFactory;
import software.amazon.awssdk.http.nio.netty.EventLoopGroupConfiguration;
import software.amazon.awssdk.http.nio.netty.NettySdkHttpClientFactory;
import software.amazon.awssdk.retry.PredefinedRetryPolicies;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.utils.BinaryUtils;
import software.amazon.awssdk.utils.IoUtils;

public class Shorea {

    private static final String BUCKET = "aws-java-sdk-v2-perf-tests";

    private static final int NUM_OBJECTS = 10_000;

    private final AtomicLong keyNumber = new AtomicLong(0);

    private final Random random = new Random();

    private Integer chunkSizeInBytes;

    public static void main(String[] args) throws Exception {
        EventLoopGroupConfiguration groupConfig = EventLoopGroupConfiguration.builder()
                                                                             .eventLoopGroupFactory(
                                                                                     DefaultEventLoopGroupFactory.builder()
                                                                                                                 .numberOfThreads(
                                                                                                                         1)
                                                                                                                 .build())
                                                                             .build();
        S3AsyncClient client = S3AsyncClient
                .builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                                                                  .retryPolicy(PredefinedRetryPolicies.NO_RETRY_POLICY)
                                                                  .build())
                .asyncHttpConfiguration(ClientAsyncHttpConfiguration
                                                .builder()
                                                .httpClientFactory(NettySdkHttpClientFactory.builder()
                                                                                            .maxConnectionsPerEndpoint(200)
                                                                                            .eventLoopGroupConfiguration(
                                                                                                    groupConfig)
                                                                                            .build())
                                                .build())
                .region(software.amazon.awssdk.regions.Region.US_WEST_2)

                .credentialsProvider(ProfileCredentialsProvider.builder()
                                                               .profileName("shared-java")
                                                               .build())
                .build();
        String objectToGet = "1.4mb.out.txt";
        Path downloadPath = Paths.get("/tmp/shorea.txt");
        GetObjectResponse response = client.getObject(GetObjectRequest.builder()
                                                                      .bucket(BUCKET)
                                                                      .key(objectToGet)
                                                                      .build(),
                                                      AsyncResponseHandler.toFile(downloadPath))
                                           .join();
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new DigestInputStream(Files.newInputStream(downloadPath), md)) {
            IoUtils.drainInputStream(is);
        }
        byte[] expectedMd5 = BinaryUtils.fromHex(response.eTag().replace("\"", ""));
        byte[] calculatedMd5 = md.digest();
        if (!Arrays.equals(expectedMd5, calculatedMd5)) {
            throw new RuntimeException(
                    String.format("Content malformed. Expected checksum was %s but calculated checksum was %s",
                                  BinaryUtils.toBase64(expectedMd5), BinaryUtils.toBase64(calculatedMd5)));
        }
    }
}
