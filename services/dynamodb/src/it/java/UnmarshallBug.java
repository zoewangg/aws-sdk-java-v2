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

import io.netty.util.ResourceLeakDetector;
import java.util.Map;
import software.amazon.awssdk.auth.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDBAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

public class UnmarshallBug {

    private static final String TABLE_NAME = "AwsJavaSdkPerfTestTable";

    public static void main(String[] args) throws Exception {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
        DynamoDBAsyncClient client =
                DynamoDBAsyncClient.builder()
                                   .region(Region.US_WEST_2)
                                   .credentialsProvider(ProfileCredentialsProvider.builder()
                                                                                  .profileName("shared-java")
                                                                                  .build())
                                   .build();

        Map<String, AttributeValue> lastEvaluatedKey = null;
        try {
            do {
                ScanResponse scanResponse = scan(client, lastEvaluatedKey);
                System.out.println(lastEvaluatedKey);
                lastEvaluatedKey = scanResponse.lastEvaluatedKey();
            } while (lastEvaluatedKey != null);
        } finally {
            client.close();
        }

    }

    private static ScanResponse scan(DynamoDBAsyncClient client,
                                     Map<String, AttributeValue> lastEvaluatedKey) {
        return client.scan(ScanRequest.builder()
                                      .limit(1000)
                                      .consistentRead(false)
                                      .tableName(TABLE_NAME)
                                      .exclusiveStartKey(lastEvaluatedKey)
                                      .build()).join();
    }
}
