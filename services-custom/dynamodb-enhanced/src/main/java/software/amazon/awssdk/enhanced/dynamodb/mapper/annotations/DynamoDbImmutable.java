/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.awssdk.enhanced.dynamodb.mapper.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.mapper.ImmutableTableSchema;

/**
 * Class level annotation that identifies this class as being a DynamoDb mappable entity. Any class used to initialize
 * a {@link ImmutableTableSchema} must have this annotation. If a class is used as an attribute type within another
 * annotated DynamoDb class, either as a document or flattened with the {@link DynamoDbFlatten} annotation, it will also
 * require this annotation to work automatically without an explicit {@link AttributeConverter}.
 * <p>
 * <b>Attribute Converter Providers</b><br>
 * Using {@link AttributeConverterProvider}s is optional and, if used, the supplied provider supersedes the default
 * converter provided by the table schema.
 * <p>
 * Note:
 * <ul>
 *     <li>The converter(s) must provide {@link AttributeConverter}s for all types used in the schema. </li>
 *     <li>The table schema DefaultAttributeConverterProvider provides standard converters for most primitive
 *     and common Java types. Use custom AttributeConverterProviders when you have specific needs for type conversion
 *     that the defaults do not cover.</li>
 *     <li>If you provide a list of attribute converter providers, you can add DefaultAttributeConverterProvider
 *     to the end of the list to fall back on the defaults.</li>
 *     <li>Providing an empty list {} will cause no providers to get loaded.</li>
 * </ul>
 *
 * Example using attribute converter providers with one custom provider and the default provider:
 *
 * {@snippet :
 * (converterProviders = {CustomAttributeConverter.class, DefaultAttributeConverterProvider.class});
 * }
 *
 * <p>
 * Example using {@link DynamoDbImmutable}:
 * {@snippet :
 * @DynamoDbImmutable(builder = Customer.Builder.class)
 * public class Customer {
 *     private final String accountId;
 *     private final int subId;
 *     private final String name;
 *     private final Instant createdDate;
 *
 *     private Customer(Builder b) {
 *         this.accountId = b.accountId;
 *         this.subId = b.subId;
 *         this.name = b.name;
 *         this.createdDate = b.createdDate;
 *     }
 *
 *     // This method will be automatically discovered and used by the TableSchema
 *     public static Builder builder() { return new Builder(); }
 *
 *     @DynamoDbPartitionKey
 *     public String accountId() { return this.accountId; }
 *
 *     @DynamoDbSortKey
 *     public int subId() { return this.subId; }
 *
 *     @DynamoDbSecondaryPartitionKey(indexNames = "customers_by_name")
 *     public String name() { return this.name; }
 *
 *     @DynamoDbSecondarySortKey(indexNames = {"customers_by_date", "customers_by_name"})
 *     public Instant createdDate() { return this.createdDate; }
 *
 *     public static final class Builder {
 *         private String accountId;
 *         private int subId;
 *         private String name;
 *         private Instant createdDate;
 *
 *         private Builder() {}
 *
 *         public Builder accountId(String accountId) { this.accountId = accountId; return this; }
 *         public Builder subId(int subId) { this.subId = subId; return this; }
 *         public Builder name(String name) { this.name = name; return this; }
 *         public Builder createdDate(Instant createdDate) { this.createdDate = createdDate; return this; }
 *
 *         // This method will be automatically discovered and used by the TableSchema
 *         public Customer build() { return new Customer(this); }
 *     }
 * }
 * }
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@SdkPublicApi
public @interface DynamoDbImmutable {
    Class<? extends AttributeConverterProvider>[] converterProviders()
            default { DefaultAttributeConverterProvider.class };

    /**
     * The builder class that can be used to construct instances of the annotated immutable class
     */
    Class<?> builder();
}
