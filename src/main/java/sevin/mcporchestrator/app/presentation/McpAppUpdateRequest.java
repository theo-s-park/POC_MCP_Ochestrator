package sevin.mcporchestrator.app.presentation;

import lombok.Data;

import java.util.Map;

@Data
public class McpAppUpdateRequest {
    private String displayName;
    private String thumbnail;
    private String clientId;
    private String redirectUri;
    private String description;
    private String category;
    private Boolean isVisible;
    // toolName → per-tool update
    private Map<String, ToolUpdate> tools;

    @Data
    public static class ToolUpdate {
        private String displayName;
        private String description;
        private String serviceType;
        private Integer deductCredit;
        private Boolean visible;
    }
}
