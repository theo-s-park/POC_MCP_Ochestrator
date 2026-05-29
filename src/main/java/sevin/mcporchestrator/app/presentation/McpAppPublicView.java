package sevin.mcporchestrator.app.presentation;

import sevin.mcporchestrator.app.domain.McpAppEntity;
import sevin.mcporchestrator.app.domain.ToolCreditInfo;
import sevin.mcporchestrator.server.application.McpServerRecord;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record McpAppPublicView(
    String id,
    String serverId,
    String displayName,
    String description,
    String thumbnail,
    String mcpUrl,
    String clientId,
    String redirectUri,
    List<ToolSummary> tools
) {
    public record ToolSummary(
        String name,
        String description,
        JsonNode inputSchema,
        String serviceType,
        int deductCredit,
        String webUrl
    ) {}

    public static McpAppPublicView of(McpAppEntity app, McpServerRecord server,
                                      Map<String, ToolCreditInfo> toolCredits) {
        String displayName = app.getDisplayName() != null ? app.getDisplayName()
            : (server != null ? server.getName() : "(unknown)");

        List<ToolSummary> tools = server == null || server.getTools() == null ? List.of() :
            server.getTools().stream()
                .filter(t -> {
                    ToolCreditInfo info = toolCredits != null ? toolCredits.get(t.getName()) : null;
                    return info == null || info.visible();
                })
                .map(t -> {
                    ToolCreditInfo info = toolCredits != null ? toolCredits.get(t.getName()) : null;
                    return new ToolSummary(
                        t.getName(),
                        t.getDescription(),
                        t.getInputSchema(),
                        info != null ? info.serviceType() : null,
                        info != null ? info.deductCredit() : 0,
                        t.getWebUrl()
                    );
                }).toList();

        return new McpAppPublicView(
            app.getId(),
            app.getMcpServerId(),
            displayName,
            app.getDescription(),
            app.getThumbnail(),
            server != null ? server.getUrl() : null,
            app.getClientId(),
            app.getRedirectUri(),
            tools
        );
    }
}
