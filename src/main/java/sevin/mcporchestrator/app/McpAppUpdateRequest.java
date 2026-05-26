package sevin.mcporchestrator.app;

import lombok.Data;

import java.util.Map;

@Data
public class McpAppUpdateRequest {
    private String displayName;
    private String thumbnail;
    private String serviceType;
    private String clientId;
    private Integer credit;
    private String description;
    private Boolean isVisible;
    private Map<String, ToolCreditInfo> toolCredits;
}
