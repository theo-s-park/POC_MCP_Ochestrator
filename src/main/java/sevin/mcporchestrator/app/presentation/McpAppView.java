package sevin.mcporchestrator.app.presentation;

import sevin.mcporchestrator.app.domain.McpAppEntity;
import sevin.mcporchestrator.app.domain.McpToolAppEntity;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.domain.McpTool;
import sevin.mcporchestrator.server.domain.ServerStatus;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        JsonNode inputSchema,
        String serviceType,
        int deductCredit,
        boolean visible,
        String webUrl
    ) {}

    /** 백오피스용 — 모든 tool 포함 */
    public static McpAppView of(McpAppEntity app, McpServerRecord server,
                                List<McpToolAppEntity> toolApps) {
        return of(app, server, toolApps, false);
    }

    /** visibleOnly=true 이면 visible=true인 tool만 포함 (public API용) */
    public static McpAppView of(McpAppEntity app, McpServerRecord server,
                                List<McpToolAppEntity> toolApps, boolean visibleOnly) {
        Map<String, McpTool> mcpToolMap = (server == null || server.getTools() == null) ? Map.of() :
            server.getTools().stream().collect(Collectors.toMap(McpTool::getName, Function.identity()));

        List<ToolView> tools = toolApps == null ? List.of() :
            toolApps.stream()
                .filter(t -> !visibleOnly || t.isVisible())
                .map(t -> {
                    McpTool raw = mcpToolMap.get(t.getToolName());
                    return new ToolView(
                        t.getToolName(),
                        t.getDisplayName() != null ? t.getDisplayName() : t.getToolName(),
                        t.getDescription() != null ? t.getDescription()
                            : (raw != null ? raw.getDescription() : null),
                        raw != null ? raw.getInputSchema() : null,
                        t.getServiceType(),
                        t.getDeductCredit(),
                        t.isVisible(),
                        t.getWebUrl() != null ? t.getWebUrl()
                            : (raw != null ? raw.getWebUrl() : null)
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
