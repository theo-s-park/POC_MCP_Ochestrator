package sevin.mcporchestrator.lambda.domain;

public record LambdaInfraResult(
    String functionName,
    String functionArn,
    String lambdaUrl,
    String ecrRepoUri,
    String s3BucketName,
    String cloudFrontDomain
) {}
