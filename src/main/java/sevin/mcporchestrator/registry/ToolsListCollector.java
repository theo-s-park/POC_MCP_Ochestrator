package sevin.mcporchestrator.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.registry.domain.CollectResult;
import sevin.mcporchestrator.registry.domain.McpTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ToolsListCollector implements McpCapabilityCollector {

    private static final Logger log = LoggerFactory.getLogger(ToolsListCollector.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final McpServerRegistry registry;

    public ToolsListCollector(McpServerRegistry registry, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    @Override
    public String method() {
        return "tools/list";
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public CollectResult collect(String serverId, String serverUrl) {
        try {
            Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", method(),
                "params", Map.of()
            );

            String responseBody = restClient.post()
                .uri(serverUrl + "/mcp")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(request))
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode toolsNode = root.path("result").path("tools");

            List<McpTool> tools = new ArrayList<>();
            for (JsonNode toolNode : toolsNode) {
                McpTool tool = new McpTool();
                tool.setName(toolNode.path("name").asText());
                tool.setDescription(toolNode.path("description").asText());
                tool.setInputSchema(toolNode.path("inputSchema"));
                tools.add(tool);
            }

            if (tools.isEmpty()) {
                log.warn("[ToolsCollector] empty tools - server {}", serverId);
                return CollectResult.EMPTY;
            }

            registry.updateTools(serverId, tools);
            log.info("[ToolsCollector] {} tool(s) collected - server {}", tools.size(), serverId);
            return CollectResult.SUCCESS;

        } catch (Exception e) {
            log.warn("[ToolsCollector] failed - server {}: {}", serverId, e.getMessage());
            return CollectResult.FAILED;
        }
    }
}
