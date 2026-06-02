package sevin.mcporchestrator.lambda.presentation;

import sevin.mcporchestrator.lambda.domain.LambdaInfraResult;

public record LambdaInfraResponse(
    String functionName,
    String functionArn,
    String lambdaUrl,
    String ecrRepoUri,
    String s3BucketName,
    String cloudFrontDomain,
    String mcpKey           // plaintext — 생성 시 한 번만 반환
) {
    public static LambdaInfraResponse from(LambdaInfraResult result) {
        return new LambdaInfraResponse(
            result.functionName(),
            result.functionArn(),
            result.lambdaUrl(),
            result.ecrRepoUri(),
            result.s3BucketName(),
            result.cloudFrontDomain(),
            result.mcpKey()
        );
    }
}
