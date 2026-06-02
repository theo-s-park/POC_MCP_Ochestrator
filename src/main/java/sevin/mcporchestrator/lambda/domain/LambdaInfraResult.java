package sevin.mcporchestrator.lambda.domain;

public record LambdaInfraResult(
    String functionName,
    String functionArn,
    String lambdaUrl,
    String ecrRepoUri,
    String s3BucketName,
    String cloudFrontDomain,
    String mcpKey          // plaintext — 한 번만 표시, 이후 조회 불가
) {}
