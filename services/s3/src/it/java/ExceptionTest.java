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

import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.async.AsyncRequestProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class ExceptionTest {

    public static void main(String[] args) throws Exception {
        S3AsyncClient client = S3AsyncClient.builder()
                                            .region(Region.US_EAST_1)
                                            .build();
        final CompletableFuture<PutObjectResponse> future = client
                .putObject(PutObjectRequest.builder()
                                           .bucket("shorea-public")
                                           .key("100mb.out")
                                           .build(),
                           AsyncRequestProvider.fromFile(Paths.get("/var/tmp/100mb.out")));

        Thread.sleep(1000);
        client.close();
        future.join();
    }
}
