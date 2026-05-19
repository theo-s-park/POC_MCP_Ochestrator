package sevin.mcporchestrator.web;

import java.util.Map;

public record WebExecuteRequest(
        String appId,
        String authorToken,
        String toolName,
        Map<String, Object> arguments,
        Integer serviceType
) {}
