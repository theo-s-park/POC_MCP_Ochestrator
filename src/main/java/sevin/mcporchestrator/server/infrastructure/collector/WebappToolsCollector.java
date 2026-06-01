package sevin.mcporchestrator.server.infrastructure.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.server.domain.McpTool;
import sevin.mcporchestrator.server.domain.ServerType;
import sevin.mcporchestrator.server.infrastructure.CollectResult;
import sevin.mcporchestrator.server.infrastructure.McpCapabilityCollector;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Component
public class WebappToolsCollector implements McpCapabilityCollector {

    private static final Logger log = LoggerFactory.getLogger(WebappToolsCollector.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final McpServerRegistry registry;

    public WebappToolsCollector(McpServerRegistry registry, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    @Override
    public String method() {
        return "GET /tools";
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public boolean supports(ServerType type) {
        return type.isWebapp();
    }

    @Override
    public CollectResult collect(String serverId, String serverUrl) {
        log.info("[WebappToolsCollector] → GET {}/tools", serverUrl);
        try {
            String responseBody = restClient.get()
                .uri(serverUrl + "/tools")
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode toolsNode = root.path("tools");

            List<McpTool> tools = new ArrayList<>();
            for (JsonNode toolNode : toolsNode) {
                McpTool tool = new McpTool();
                tool.setName(toolNode.path("name").asText());
                tool.setDescription(toolNode.path("description").asText());
                tool.setInputSchema(toolNode.path("inputSchema"));
                tool.setWebUrl(toolNode.path("webUrl").asText(null));
                tools.add(tool);
            }

            if (tools.isEmpty()) {
                log.warn("[WebappToolsCollector] empty tools - server {}", serverId);
                return CollectResult.EMPTY;
            }

            registry.updateTools(serverId, tools);
            log.info("[WebappToolsCollector] {} tool(s) collected - server {}", tools.size(), serverId);
            return CollectResult.SUCCESS;

        } catch (Exception e) {
            log.warn("[WebappToolsCollector] failed - server {}: {}", serverId, e.getMessage());
            return CollectResult.FAILED;
        }
    }
}
