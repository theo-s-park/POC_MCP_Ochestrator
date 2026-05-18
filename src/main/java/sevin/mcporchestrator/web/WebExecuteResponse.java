package sevin.mcporchestrator.web;

import tools.jackson.databind.JsonNode;

public record WebExecuteResponse(
        String appId,
        String toolName,
        JsonNode result,
        int creditUsed
) {}
