package sevin.mcporchestrator.server.infrastructure.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.lambda.application.McpKeyService;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.domain.McpResource;
import sevin.mcporchestrator.server.domain.ServerType;
import sevin.mcporchestrator.server.infrastructure.CollectResult;
import sevin.mcporchestrator.server.infrastructure.McpCapabilityCollector;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ResourcesListCollector implements McpCapabilityCollector {

    private static final Logger log = LoggerFactory.getLogger(ResourcesListCollector.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final McpServerRegistry registry;
    private final McpKeyService mcpKeyService;

    public ResourcesListCollector(McpServerRegistry registry, McpKeyService mcpKeyService, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.mcpKeyService = mcpKeyService;
    }

    @Override
    public String method() {
        return "resources/list";
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    @Override
    public boolean supports(ServerType type) {
        return type.isMcp();
    }

    @Override
    public CollectResult collect(String serverId, String serverUrl) {
        log.info("[ResourcesCollector] → POST {}/mcp resources/list", serverUrl);
        try {
            Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", method(),
                "params", Map.of()
            );

            McpServerRecord server = registry.find(serverId).orElse(null);
            var spec = restClient.post()
                .uri(serverUrl + "/mcp")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
            if (server != null && server.getMcpKeyEncrypted() != null) {
                try { spec = spec.header("X-MCP-KEY", mcpKeyService.decrypt(server.getMcpKeyEncrypted())); }
                catch (Exception ignored) {}
            }
            String responseBody = spec.body(objectMapper.writeValueAsString(request))
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode resourcesNode = root.path("result").path("resources");

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
                log.info("[ResourcesCollector] empty resources (optional) - server {}", serverId);
                return CollectResult.EMPTY;
            }

            registry.updateResources(serverId, resources);
            log.info("[ResourcesCollector] {} resource(s) collected - server {}", resources.size(), serverId);
            return CollectResult.SUCCESS;

        } catch (Exception e) {
            log.warn("[ResourcesCollector] failed (optional) - server {}: {}", serverId, e.getMessage());
            return CollectResult.FAILED;
        }
    }
}
