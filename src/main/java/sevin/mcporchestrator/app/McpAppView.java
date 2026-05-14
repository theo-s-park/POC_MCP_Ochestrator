package sevin.mcporchestrator.app;

import sevin.mcporchestrator.registry.domain.McpServerRecord;
import sevin.mcporchestrator.registry.domain.ServerStatus;

public record McpAppView(
    String id,
    String mcpServerId,
    String serverName,
    String serverUrl,
    ServerStatus serverStatus,
    String displayName,
    String thumbnail,
    int credit,
    String description,
    boolean isVisible,
    String serverDescription
) {
    public static McpAppView of(McpAppEntity app, McpServerRecord server) {
        return new McpAppView(
            app.getId(),
            app.getMcpServerId(),
            server != null ? server.getName() : "(unknown)",
            server != null ? server.getUrl() : "",
            server != null ? server.getStatus() : ServerStatus.INACTIVE,
            app.getDisplayName(),
            app.getThumbnail(),
            app.getCredit(),
            app.getDescription(),
            app.isVisible(),
            server != null ? server.getDescription() : null
        );
    }
}
