package sevin.mcporchestrator.app.presentation;

import lombok.Data;
import sevin.mcporchestrator.app.domain.ToolCreditInfo;

import java.util.Map;

@Data
public class McpAppUpdateRequest {
    private String displayName;
    private String thumbnail;
    private String serviceType;
    private String clientId;
    private String redirectUri;
    private Integer credit;
    private String description;
    private Boolean isVisible;
    private Map<String, ToolCreditInfo> toolCredits;
}
