package sevin.mcporchestrator.registry.domain;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class McpServerRecord {
    private String serverId;
    private String name;
    private String url;
    private String description;
    private String version;
    private ServerStatus status;
    private List<McpTool> tools;
    private List<McpResource> resources;
    private Instant registeredAt;
    private int healthCheckFailures;
}
