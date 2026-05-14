package sevin.mcporchestrator.registry.domain;

import lombok.Data;

@Data
public class McpResource {
    private String uri;
    private String name;
    private String description;
    private String mimeType;
}
