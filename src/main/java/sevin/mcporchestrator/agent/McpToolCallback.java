package sevin.mcporchestrator.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.registry.McpServerRegistry;
import sevin.mcporchestrator.registry.domain.McpServerRecord;
import sevin.mcporchestrator.registry.domain.McpTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class McpToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(McpToolCallback.class);

    private final McpTool tool;
    private final McpServerRegistry registry;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String sessionId;
    private final List<AgentResponse.ToolTrace> trace;

    public McpToolCallback(McpTool tool, McpServerRegistry registry, RestClient restClient, ObjectMapper objectMapper, String sessionId, List<AgentResponse.ToolTrace> trace) {
        this.tool = tool;
        this.registry = registry;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.sessionId = sessionId;
        this.trace = trace;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        String schema = (tool.getInputSchema() != null) ? tool.getInputSchema().toString() : "{}";
        return ToolDefinition.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .inputSchema(schema)
                .build();
    }

    @Override
    public String call(String toolInput) {
        try {
            McpServerRecord server = registry.findByToolName(tool.getName())
                    .orElseThrow(() -> new IllegalStateException("Server not found for tool: " + tool.getName()));

            JsonNode args = objectMapper.readTree(toolInput);
            log.info("[Agent][{}] tool call -> {} | server={} | args={}",
                    sessionId, tool.getName(), server.getName(), args);

            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "tools/call",
                    "params", Map.of("name", tool.getName(), "arguments", args)
            );

            String response = restClient.post()
                    .uri(server.getUrl() + "/mcp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(request))
                    .retrieve()
                    .body(String.class);

            String result = objectMapper.readTree(response).path("result").toString();
            log.info("[Agent][{}] tool result <- {} | result={}", sessionId, tool.getName(), result);
            trace.add(new AgentResponse.ToolTrace(tool.getName(), server.getName(), args.toString(), result));
            return result;

        } catch (Exception e) {
            log.error("[Agent][{}] tool error - {} | {}", sessionId, tool.getName(), e.getMessage());
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
