package sevin.mcporchestrator.app.presentation;

import sevin.mcporchestrator.app.domain.McpAppEntity;
import sevin.mcporchestrator.app.domain.McpToolAppEntity;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.domain.McpTool;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record McpAppPublicView(
    String id,
    String serverId,
    String displayName,
    String description,
    String thumbnail,
    String category,
    String mcpUrl,
    String clientId,
    String redirectUri,
    List<ToolSummary> tools
) {
    public record ToolSummary(
        String name,
        String displayName,
        String description,
        JsonNode inputSchema,
        String serviceType,
        int deductCredit,
        String webUrl
    ) {}

    public static McpAppPublicView of(McpAppEntity app, McpServerRecord server,
                                      List<McpToolAppEntity> toolApps) {
        String displayName = app.getDisplayName() != null ? app.getDisplayName()
            : (server != null ? server.getName() : "(unknown)");

        Map<String, McpToolAppEntity> toolAppMap = toolApps == null ? Map.of() :
            toolApps.stream().collect(Collectors.toMap(McpToolAppEntity::getToolName, Function.identity()));

        Map<String, McpTool> mcpToolMap = (server == null || server.getTools() == null) ? Map.of() :
            server.getTools().stream().collect(Collectors.toMap(McpTool::getName, Function.identity()));

        List<ToolSummary> tools = toolApps == null ? List.of() :
            toolApps.stream()
                .filter(McpToolAppEntity::isVisible)
                .map(t -> {
                    McpTool mcpTool = mcpToolMap.get(t.getToolName());
                    return new ToolSummary(
                        t.getToolName(),
                        t.getDisplayName() != null ? t.getDisplayName() : t.getToolName(),
                        t.getDescription() != null ? t.getDescription()
                            : (mcpTool != null ? mcpTool.getDescription() : null),
                        mcpTool != null ? mcpTool.getInputSchema() : null,
                        t.getServiceType(),
                        t.getDeductCredit(),
                        t.getWebUrl() != null ? t.getWebUrl()
                            : (mcpTool != null ? mcpTool.getWebUrl() : null)
                    );
                }).toList();

        return new McpAppPublicView(
            app.getId(),
            app.getMcpServerId(),
            displayName,
            app.getDescription(),
            app.getThumbnail(),
            app.getCategory(),
            server != null ? server.getUrl() : null,
            app.getClientId(),
            app.getRedirectUri(),
            tools
        );
    }
}
