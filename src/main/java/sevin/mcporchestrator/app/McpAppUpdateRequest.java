package sevin.mcporchestrator.app;

import lombok.Data;

@Data
public class McpAppUpdateRequest {
    private String displayName;
    private String thumbnail;
    private Integer credit;
    private String description;
    private Boolean isVisible;
    private String serverDescription;
}
