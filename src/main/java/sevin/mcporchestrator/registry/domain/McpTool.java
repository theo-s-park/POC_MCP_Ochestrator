package sevin.mcporchestrator.registry.domain;

import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class McpTool {
    private String name;
    private String description;
    private JsonNode inputSchema;
}
