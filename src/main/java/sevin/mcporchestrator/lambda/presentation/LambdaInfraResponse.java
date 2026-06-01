package sevin.mcporchestrator.lambda.presentation;

import sevin.mcporchestrator.lambda.domain.LambdaInfraResult;

public record LambdaInfraResponse(
    String functionName,
    String functionArn,
    String lambdaUrl,
    String ecrRepoUri,
    String s3BucketName,
    String cloudFrontDomain
) {
    public static LambdaInfraResponse from(LambdaInfraResult result) {
        return new LambdaInfraResponse(
            result.functionName(),
            result.functionArn(),
            result.lambdaUrl(),
            result.ecrRepoUri(),
            result.s3BucketName(),
            result.cloudFrontDomain()
        );
    }
}
