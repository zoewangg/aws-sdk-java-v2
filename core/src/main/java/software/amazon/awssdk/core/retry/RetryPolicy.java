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

package software.amazon.awssdk.core.retry;

import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.core.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.FixedDelayBackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.AndRetryCondition;
import software.amazon.awssdk.core.retry.conditions.MaxNumberOfRetriesCondition;
import software.amazon.awssdk.core.retry.conditions.OrRetryCondition;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.core.retry.conditions.RetryOnErrorCodeCondition;
import software.amazon.awssdk.core.retry.conditions.RetryOnExceptionsCondition;
import software.amazon.awssdk.core.retry.conditions.RetryOnStatusCodeCondition;
import software.amazon.awssdk.utils.builder.CopyableBuilder;
import software.amazon.awssdk.utils.builder.ToCopyableBuilder;

/**
 * Interface for specifying a retry policy to use when evaluating whether or not a request should be retried. An implementation
 * of this interface can be provided to {@link ClientOverrideConfiguration#retryPolicy} or the {@link #builder()}} can be used
 * to construct a retry policy from SDK provided policies or policies that directly implement {@link BackoffStrategy} and/or
 * {@link RetryCondition}.
 *
 * When using the {@link #builder()} the SDK will use default values for fields that are not provided. The default number of
 * retries that will be used is {@link SdkDefaultRetrySettings#DEFAULT_NUM_RETRIES}. The default retry condition is
 * {@link RetryCondition#DEFAULT} and the default backoff strategy is {@link BackoffStrategy#DEFAULT}.
 *
 * @see RetryCondition for a list of SDK provided retry condition strategies
 * @see BackoffStrategy for a list of SDK provided backoff strategies
 */
@SdkPublicApi
public final class RetryPolicy implements ToCopyableBuilder<RetryPolicy.Builder, RetryPolicy> {

    public static final RetryPolicy DEFAULT = RetryPolicy.builder()
                                                         .backoffStrategy(BackoffStrategy.DEFAULT)
                                                         .numRetries(SdkDefaultRetrySettings.DEFAULT_NUM_RETRIES)
                                                         .retryCondition(RetryCondition.DEFAULT)
                                                         .build();

    public static final RetryPolicy NONE = RetryPolicy.builder()
                                                      .backoffStrategy(BackoffStrategy.NONE)
                                                      .retryCondition(RetryCondition.NONE)
                                                      .build();

    private final RetryCondition retryCondition;
    private final BackoffStrategy backoffStrategy;
    private final Integer numRetries;

    RetryPolicy(Builder builder) {
        this.backoffStrategy = builder.backoffStrategy() != null ? builder.backoffStrategy() : BackoffStrategy.DEFAULT;

        int numRetries = builder.numRetries() != null ? builder.numRetries() : SdkDefaultRetrySettings.DEFAULT_NUM_RETRIES;
        this.numRetries = numRetries;

        RetryCondition condition = builder.retryCondition() != null ? builder.retryCondition() : RetryCondition.DEFAULT;
        this.retryCondition = new AndRetryCondition(new MaxNumberOfRetriesCondition(numRetries),
                                                    condition);
    }

    public RetryCondition retryCondition() {
        return retryCondition;
    }

    public BackoffStrategy backoffStrategy() {
        return backoffStrategy;
    }

    public Integer numRetries() {
        return numRetries;
    }

    public static Builder builder() {
        return new BuilderImpl();
    }

    @Override
    public Builder toBuilder() {
        return builder().numRetries(numRetries).retryCondition(retryCondition).backoffStrategy(backoffStrategy);
    }

    /**
     * Builder interface for {@link RetryPolicy}
     */
    public interface Builder extends CopyableBuilder<Builder, RetryPolicy> {

        /**
         * Specifies the maximum number of retries to be executed for a request.
         *
         * @param numRetries Number of retries
         * @return This builder for method chaining
         */
        Builder numRetries(Integer numRetries);

        /**
         * The number of retries configured with {@link #numRetries()}.
         */
        Integer numRetries();

        /**
         * Specifies the backoff strategy to use when retrying requests.
         *
         * @param backoffStrategy The backoff strategy
         * @return This builder for method chaining
         * @see BackoffStrategy
         * @see EqualJitterBackoffStrategy
         * @see FixedDelayBackoffStrategy
         * @see FullJitterBackoffStrategy
         */
        Builder backoffStrategy(BackoffStrategy backoffStrategy);

        /**
         * The backoff strategy configured with {@link #backoffStrategy()}.
         */
        BackoffStrategy backoffStrategy();

        /**
         * Specifies the retry condition to use when retrying requests.
         *
         * @param retryCondition The retry condition
         * @return This builder for method chaining
         * @see RetryCondition
         * @see AndRetryCondition
         * @see OrRetryCondition
         * @see RetryOnErrorCodeCondition
         * @see RetryOnStatusCodeCondition
         * @see RetryOnExceptionsCondition
         */
        Builder retryCondition(RetryCondition retryCondition);

        /**
         * The retry condition configured with {@link #retryCondition()}.
         */
        RetryCondition retryCondition();
    }

    /**
     * Builder for a {@link RetryPolicy}.
     */
    public static final class BuilderImpl implements Builder {

        private Integer numRetries;
        private BackoffStrategy backoffStrategy;
        private RetryCondition retryCondition;

        @Override
        public RetryPolicy.Builder numRetries(Integer numRetries) {
            this.numRetries = numRetries;
            return this;
        }

        @Override
        public Integer numRetries() {
            return numRetries;
        }

        @Override
        public RetryPolicy.Builder backoffStrategy(BackoffStrategy backoffStrategy) {
            this.backoffStrategy = backoffStrategy;
            return this;
        }

        @Override
        public BackoffStrategy backoffStrategy() {
            return backoffStrategy;
        }

        @Override
        public RetryPolicy.Builder retryCondition(RetryCondition retryCondition) {
            this.retryCondition = retryCondition;
            return this;
        }

        @Override
        public RetryCondition retryCondition() {
            return retryCondition;
        }

        @Override
        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
