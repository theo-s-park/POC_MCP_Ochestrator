package sevin.mcporchestrator.oss;

public record OssAiServiceInfo(
        long id,
        int type,
        int status,
        int deductCredit,
        String serviceDesc
) {}
