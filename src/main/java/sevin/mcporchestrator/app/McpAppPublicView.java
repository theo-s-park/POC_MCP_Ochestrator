package sevin.mcporchestrator.app;

import sevin.mcporchestrator.registry.domain.McpServerRecord;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record McpAppPublicView(
    String id,
    String displayName,
    String description,
    String thumbnail,
    int credit,
    String mcpUrl,
    List<ToolSummary> tools
) {
    public record ToolSummary(
        String name,
        String description,
        JsonNode inputSchema
    ) {}

    public static McpAppPublicView of(McpAppEntity app, McpServerRecord server) {
        String displayName = app.getDisplayName() != null ? app.getDisplayName()
            : (server != null ? server.getName() : "(unknown)");

        List<ToolSummary> tools = server == null || server.getTools() == null ? List.of() :
            server.getTools().stream()
                .map(t -> new ToolSummary(t.getName(), t.getDescription(), t.getInputSchema()))
                .toList();

        return new McpAppPublicView(
            app.getId(),
            displayName,
            app.getDescription(),
            app.getThumbnail(),
            app.getCredit(),
            server != null ? server.getUrl() : null,
            tools
        );
    }
}
