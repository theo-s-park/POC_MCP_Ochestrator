package sevin.mcporchestrator.server.application;

import lombok.Builder;
import lombok.Data;
import sevin.mcporchestrator.server.domain.McpResource;
import sevin.mcporchestrator.server.domain.McpTool;
import sevin.mcporchestrator.server.domain.ServerStatus;
import sevin.mcporchestrator.server.domain.ServerType;

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
    @Builder.Default
    private ServerType type = ServerType.MCP;
    private ServerStatus status;
    private List<McpTool> tools;
    private List<McpResource> resources;
    private Instant registeredAt;
    private int healthCheckFailures;
    private String webAppUrl;
    private Boolean webHealthOk;
}
