package sevin.mcporchestrator.lambda.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsClientConfig {

    @Bean
    public LambdaClient lambdaClient(@Value("${aws.region:ap-northeast-2}") String region) {
        return LambdaClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    public EcrClient ecrClient(@Value("${aws.region:ap-northeast-2}") String region) {
        return EcrClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    public S3Client s3Client(@Value("${aws.region:ap-northeast-2}") String region) {
        return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    public CloudFrontClient cloudFrontClient() {
        return CloudFrontClient.builder()
            .region(Region.AWS_GLOBAL)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
}
