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

package software.amazon.awssdk.services.simpledb.transform;

import static org.junit.Assert.assertTrue;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import org.junit.Test;
import software.amazon.awssdk.awscore.protocol.xml.StaxUnmarshallerContext;
import software.amazon.awssdk.services.simpledb.model.DomainMetadataResponse;
import software.amazon.awssdk.utils.XmlUtils;

public class DomainMetadataResultUnmarshallerTest {

    /**
     * Test method for DomainMetadataResultXpathUnmarshaller
     */
    @Test
    public final void testXpathUnmarshaller() throws Exception {
        XMLInputFactory xmlInputFactory = XmlUtils.xmlInputFactory();
        XMLEventReader eventReader = xmlInputFactory.createXMLEventReader(DomainMetadataResultUnmarshallerTest.class
                                                                                  .getResourceAsStream("DomainMetadataResponse.xml"));
        StaxUnmarshallerContext unmarshallerContext = new StaxUnmarshallerContext(eventReader);
        DomainMetadataResponse result = new DomainMetadataResponseUnmarshaller()
                .unmarshall(unmarshallerContext);

        assertTrue(result.itemCount() == 25);
        assertTrue(result.itemNamesSizeBytes() == 12345);
        assertTrue(result.attributeNameCount() == 20);
        assertTrue(result.attributeNamesSizeBytes() == 2345);
        assertTrue(result.attributeValueCount() == 25);
        assertTrue(result.attributeValuesSizeBytes() == 1234);
        assertTrue(result.timestamp() == 5555);
    }

}
