package com.java.doc_process.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

@Configuration
public class AwsClientConfiguration {

    @Value("${aws.region.static}")
    private String region;

    @Value("${aws.endpoint:}")
    private String endpoint;

    @Value("${aws.credentials.access-key:}")
    private String accessKey;

    @Value("${aws.credentials.secret-key:}")
    private String secretKey;

    @Value("${aws.s3.path-style-access:false}")
    private boolean pathStyleAccess;

    @Value("${aws.client.api-call-timeout:10s}")
    private Duration apiCallTimeout;

    @Value("${aws.client.api-call-attempt-timeout:3s}")
    private Duration apiCallAttemptTimeout;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .overrideConfiguration(clientOverrideConfiguration())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build());
        applyEndpoint(builder);
        return builder.build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .overrideConfiguration(clientOverrideConfiguration());
        applyEndpoint(builder);
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        SqsAsyncClientBuilder builder = SqsAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .overrideConfiguration(clientOverrideConfiguration());
        applyEndpoint(builder);
        return builder.build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            return DefaultCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private ClientOverrideConfiguration clientOverrideConfiguration() {
        if (apiCallTimeout.isNegative()
                || apiCallTimeout.isZero()
                || apiCallAttemptTimeout.isNegative()
                || apiCallAttemptTimeout.isZero()
                || apiCallTimeout.compareTo(apiCallAttemptTimeout) < 0) {
            throw new IllegalArgumentException(
                    "AWS API call timeout must be positive and no shorter than the attempt timeout");
        }
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(apiCallTimeout)
                .apiCallAttemptTimeout(apiCallAttemptTimeout)
                .build();
    }

    private void applyEndpoint(S3ClientBuilder builder) {
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
    }

    private void applyEndpoint(DynamoDbClientBuilder builder) {
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
    }

    private void applyEndpoint(SqsAsyncClientBuilder builder) {
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
    }
}
