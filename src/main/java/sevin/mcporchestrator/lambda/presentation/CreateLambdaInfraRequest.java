package sevin.mcporchestrator.lambda.presentation;

import sevin.mcporchestrator.lambda.domain.LambdaRuntime;

public record CreateLambdaInfraRequest(
    String functionName,
    LambdaRuntime runtime,
    Integer timeout,
    Integer memorySize
) {}
