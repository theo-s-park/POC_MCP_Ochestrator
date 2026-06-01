package sevin.mcporchestrator.infra.domain;

public record LambdaCreateRequest(
    String functionName,
    String ecrImageUri,
    int memory,
    int timeout
) {
    public LambdaCreateRequest {
        if (functionName == null || functionName.isBlank()) throw new IllegalArgumentException("functionName required");
        if (ecrImageUri == null || ecrImageUri.isBlank()) throw new IllegalArgumentException("ecrImageUri required");
        if (memory <= 0) memory = 512;
        if (timeout <= 0) timeout = 30;
    }
}
