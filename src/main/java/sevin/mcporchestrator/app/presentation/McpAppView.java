package sevin.mcporchestrator.app.presentation;

import sevin.mcporchestrator.app.domain.McpAppEntity;
import sevin.mcporchestrator.app.domain.ToolCreditInfo;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.domain.ServerStatus;

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
    String redirectUri,
    int credit,
    String description,
    boolean isVisible,
    List<ToolCreditView> tools,
    String webAppUrl,
    Boolean webHealthOk
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
            app.getRedirectUri(),
            app.getCredit(),
            app.getDescription(),
            app.isVisible(),
            tools,
            server != null ? server.getWebAppUrl() : null,
            server != null ? server.getWebHealthOk() : null
        );
    }
}
