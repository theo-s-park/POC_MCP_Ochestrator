package sevin.mcporchestrator.app;

import sevin.mcporchestrator.registry.domain.McpServerRecord;
import sevin.mcporchestrator.registry.domain.ServerStatus;

import java.util.List;
import java.util.Map;

public record McpAppView(
    String id,
    String mcpServerId,
    String serverName,
    String serverUrl,
    ServerStatus serverStatus,
    String displayName,
    String thumbnail,
    String serviceType,
    String clientId,
    int credit,
    String description,
    boolean isVisible,
    List<ToolCreditView> tools
) {
    public record ToolCreditView(String toolName, String serviceType, int deductCredit, boolean visible) {}

    public static McpAppView of(McpAppEntity app, McpServerRecord server,
                                Map<String, ToolCreditInfo> toolCredits) {
        List<ToolCreditView> tools = server == null || server.getTools() == null ? List.of() :
            server.getTools().stream().map(t -> {
                ToolCreditInfo info = toolCredits != null ? toolCredits.get(t.getName()) : null;
                return new ToolCreditView(
                    t.getName(),
                    info != null ? info.serviceType() : null,
                    info != null ? info.deductCredit() : 0,
                    info == null || info.visible()
                );
            }).toList();

        return new McpAppView(
            app.getId(),
            app.getMcpServerId(),
            server != null ? server.getName() : "(unknown)",
            server != null ? server.getUrl() : "",
            server != null ? server.getStatus() : ServerStatus.INACTIVE,
            app.getDisplayName(),
            app.getThumbnail(),
            app.getServiceType(),
            app.getClientId(),
            app.getCredit(),
            app.getDescription(),
            app.isVisible(),
            tools
        );
    }
}
