package sevin.mcporchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sevin.mcporchestrator.infra.domain.AwsRegion;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.lambda.LambdaClient;

@Configuration
public class AwsConfig {

    @Bean
    public LambdaClient lambdaClient() {
        return LambdaClient.builder()
            .region(AwsRegion.SEOUL.toSdkRegion())
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    public EcrClient ecrClient() {
        return EcrClient.builder()
            .region(AwsRegion.SEOUL.toSdkRegion())
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
}
