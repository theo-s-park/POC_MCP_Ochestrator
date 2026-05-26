package sevin.mcporchestrator.oss;

public record OssAiServiceInfo(
        String serviceType,
        String status,
        int deductCredit,
        String serviceDesc,
        Integer inputLimit
) {}
