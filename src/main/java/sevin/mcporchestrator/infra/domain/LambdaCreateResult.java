package sevin.mcporchestrator.infra.domain;

public record LambdaCreateResult(
    String functionName,
    String functionArn,
    String lambdaUrl,
    String ecrImageUri,
    String roleArn
) {}
