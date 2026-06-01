package sevin.mcporchestrator.lambda.application;

public record LambdaInfraStep(
    String step,    // ECR | LAMBDA | URL | S3 | CLOUDFRONT
    String status,  // running | done | error
    String value    // 생성된 리소스 URI/URL 또는 에러 메시지
) {}
