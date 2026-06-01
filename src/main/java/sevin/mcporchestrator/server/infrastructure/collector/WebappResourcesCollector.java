package sevin.mcporchestrator.server.infrastructure.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.server.domain.McpResource;
import sevin.mcporchestrator.server.domain.ServerType;
import sevin.mcporchestrator.server.infrastructure.CollectResult;
import sevin.mcporchestrator.server.infrastructure.McpCapabilityCollector;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Component
public class WebappResourcesCollector implements McpCapabilityCollector {

    private static final Logger log = LoggerFactory.getLogger(WebappResourcesCollector.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final McpServerRegistry registry;

    public WebappResourcesCollector(McpServerRegistry registry, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    @Override
    public String method() {
        return "GET /resources";
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    @Override
    public boolean supports(ServerType type) {
        return type.isWebapp();
    }

    @Override
    public CollectResult collect(String serverId, String serverUrl) {
        log.info("[WebappResourcesCollector] → GET {}/resources", serverUrl);
        try {
            String responseBody = restClient.get()
                .uri(serverUrl + "/resources")
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode resourcesNode = root.path("resources");

            List<McpResource> resources = new ArrayList<>();
            for (JsonNode node : resourcesNode) {
                McpResource resource = new McpResource();
                resource.setUri(node.path("uri").asText());
                resource.setName(node.path("name").asText());
                resource.setDescription(node.path("description").asText(null));
                resource.setMimeType(node.path("mimeType").asText(null));
                resources.add(resource);
            }

            if (resources.isEmpty()) {
                log.info("[WebappResourcesCollector] empty resources (optional) - server {}", serverId);
                return CollectResult.EMPTY;
            }

            registry.updateResources(serverId, resources);
            log.info("[WebappResourcesCollector] {} resource(s) collected - server {}", resources.size(), serverId);
            return CollectResult.SUCCESS;

        } catch (Exception e) {
            log.warn("[WebappResourcesCollector] failed (optional) - server {}: {}", serverId, e.getMessage());
            return CollectResult.FAILED;
        }
    }
}
