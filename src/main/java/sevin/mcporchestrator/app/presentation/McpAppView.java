package sevin.mcporchestrator.app.presentation;

import sevin.mcporchestrator.app.domain.McpAppEntity;
import sevin.mcporchestrator.app.domain.McpToolAppEntity;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.domain.ServerStatus;

import java.util.List;

public record McpAppView(
    String id,
    String mcpServerId,
    String serverName,
    String serverUrl,
    ServerStatus serverStatus,
    String displayName,
    String thumbnail,
    String clientId,
    String redirectUri,
    String description,
    String category,
    boolean isVisible,
    List<ToolView> tools,
    String webAppUrl,
    Boolean webHealthOk
) {
    public record ToolView(
        String toolName,
        String displayName,
        String description,
        String serviceType,
        int deductCredit,
        boolean visible
    ) {}

    public static McpAppView of(McpAppEntity app, McpServerRecord server,
                                List<McpToolAppEntity> toolApps) {
        List<ToolView> tools = toolApps == null ? List.of() :
            toolApps.stream().map(t -> new ToolView(
                t.getToolName(),
                t.getDisplayName(),
                t.getDescription(),
                t.getServiceType(),
                t.getDeductCredit(),
                t.isVisible()
            )).toList();

        return new McpAppView(
            app.getId(),
            app.getMcpServerId(),
            server != null ? server.getName() : "(unknown)",
            server != null ? server.getUrl() : "",
            server != null ? server.getStatus() : ServerStatus.INACTIVE,
            app.getDisplayName(),
            app.getThumbnail(),
            app.getClientId(),
            app.getRedirectUri(),
            app.getDescription(),
            app.getCategory(),
            app.isVisible(),
            tools,
            server != null ? server.getWebAppUrl() : null,
            server != null ? server.getWebHealthOk() : null
        );
    }
}
