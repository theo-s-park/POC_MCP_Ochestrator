package sevin.mcporchestrator.server.presentation;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.domain.McpTool;
import sevin.mcporchestrator.server.domain.ServerStatus;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Tag(name = "MCP Proxy", description = "MCP JSON-RPC 2.0 프록시 (tools/list, tools/call, initialize, ping)")
@RestController
public class McpProxyController {

    private static final Logger log = LoggerFactory.getLogger(McpProxyController.class);

    private final McpServerRegistry registry;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public McpProxyController(McpServerRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();
    }

    @PostMapping("/mcp")
    public JsonNode handle(@RequestBody JsonNode body) {
        String method = body.path("method").asText();
        int id = body.path("id").asInt(1);

        return switch (method) {
            case "initialize"   -> ok(id, buildInitializeResult());
            case "ping"         -> ok(id, objectMapper.createObjectNode());
            case "tools/list"   -> ok(id, buildToolsList());
            case "tools/call"   -> handleToolsCall(id, body.path("params"));
            default             -> error(id, -32601, "Method not found: " + method);
        };
    }

    private ObjectNode buildInitializeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        ObjectNode info = objectMapper.createObjectNode();
        info.put("name", "mcp-hub");
        info.put("version", "1.0.0");
        result.set("serverInfo", info);
        ObjectNode caps = objectMapper.createObjectNode();
        caps.set("tools", objectMapper.createObjectNode());
        result.set("capabilities", caps);
        return result;
    }

    private ObjectNode buildToolsList() {
        ArrayNode tools = objectMapper.createArrayNode();
        List<McpServerRecord> activeServers = registry.findAll().stream()
            .filter(s -> s.getStatus() == ServerStatus.ACTIVE && s.getTools() != null && !s.getTools().isEmpty())
            .toList();

        for (McpServerRecord server : activeServers) {
            if (server.getTools() == null) continue;
            for (McpTool tool : server.getTools()) {
                ObjectNode t = objectMapper.createObjectNode();
                t.put("name", tool.getName());
                t.put("description", tool.getDescription() != null ? tool.getDescription() : "");
                t.set("inputSchema", tool.getInputSchema() != null
                    ? tool.getInputSchema()
                    : objectMapper.createObjectNode().put("type", "object"));
                tools.add(t);
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", tools);
        return result;
    }

    private JsonNode handleToolsCall(int id, JsonNode params) {
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");

        Optional<McpServerRecord> serverOpt = registry.findByToolName(toolName);
        if (serverOpt.isEmpty()) {
            log.warn("[McpProxy] tools/call: tool '{}' not found in any registered server", toolName);
            return error(id, -32602, "Tool not found: " + toolName);
        }

        McpServerRecord server = serverOpt.get();
        try {
            Map<String, Object> req = Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", "tools/call",
                "params", Map.of("name", toolName, "arguments", arguments)
            );
            String responseBody = restClient.post()
                .uri(server.getUrl() + "/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(req))
                .retrieve()
                .body(String.class);
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.error("[McpProxy] tools/call proxy failed - server {}, tool {}: {}", server.getServerId(), toolName, e.getMessage());
            return error(id, -32603, "Upstream error: " + e.getMessage());
        }
    }

    private JsonNode ok(int id, JsonNode result) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", id);
        node.set("result", result);
        return node;
    }

    private JsonNode error(int id, int code, String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", id);
        ObjectNode err = objectMapper.createObjectNode();
        err.put("code", code);
        err.put("message", message);
        node.set("error", err);
        return node;
    }
}
