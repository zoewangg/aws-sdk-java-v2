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

import org.junit.Test;
import software.amazon.awssdk.client.builder.ClientAsyncHttpConfiguration;
import software.amazon.awssdk.http.nio.netty.NettySdkHttpClientFactory;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;

public class ConnectionCloseIntegrationTest {

    @Test
    public void test() {
        S3AsyncClient client =
                S3AsyncClient.builder()
                             .asyncHttpConfiguration(
                                     ClientAsyncHttpConfiguration
                                             .builder()
                                             .httpClientFactory(NettySdkHttpClientFactory.builder()
                                                                                         .maxConnectionsPerEndpoint(
                                                                                                 1)
                                                                                         .build())
                                             .build())
                             .build();

        for(int i = 0; i < 101; i++) {
            client.listBuckets(ListBucketsRequest.builder().build()).join();
        }
        client.listBuckets(ListBucketsRequest.builder().build()).join();
        System.out.println();
    }
}
