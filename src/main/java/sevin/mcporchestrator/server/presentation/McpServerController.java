package sevin.mcporchestrator.server.presentation;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.app.domain.McpAppEntity;
import sevin.mcporchestrator.app.infrastructure.McpAppRepository;
import sevin.mcporchestrator.common.exception.ErrorCode;
import sevin.mcporchestrator.common.exception.McpException;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.application.McpServerService;
import sevin.mcporchestrator.server.exception.ServerNotFoundException;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "MCP Servers", description = "MCP 서버 등록·조회·삭제 및 capability probe")
@RestController
@RequestMapping("/api/mcp/servers")
public class McpServerController {

    private final McpServerService service;
    private final McpAppRepository mcpAppRepository;
    private final McpServerRegistry registry;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public McpServerController(McpServerService service, McpAppRepository mcpAppRepository,
                               McpServerRegistry registry, ObjectMapper objectMapper) {
        this.service = service;
        this.mcpAppRepository = mcpAppRepository;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        if (request.getUrl() == null || request.getUrl().isBlank()) {
            throw new McpException(ErrorCode.SERVER_URL_REQUIRED);
        }
        McpServerRecord record = service.register(request.getUrl(), request.getName(), request.getWebAppUrl());
        return ResponseEntity.ok(Map.of(
            "serverId", record.getServerId(),
            "status", record.getStatus()
        ));
    }

    @DeleteMapping("/{serverId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String serverId) {
        service.delete(serverId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/{serverId}/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@PathVariable String serverId) {
        McpServerRecord record = service.refresh(serverId);
        return ResponseEntity.ok(Map.of(
            "serverId", record.getServerId(),
            "status", record.getStatus()
        ));
    }

    @GetMapping("/{serverId}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String serverId) {
        McpServerRecord server = registry.find(serverId)
            .orElseThrow(ServerNotFoundException::new);
        McpAppEntity app = mcpAppRepository.findByMcpServerId(serverId).orElse(null);
        return ResponseEntity.ok(toServerMap(server, app));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        Map<String, McpAppEntity> appByServerId = mcpAppRepository.findAll().stream()
            .collect(Collectors.toMap(McpAppEntity::getMcpServerId, a -> a));

        List<Map<String, Object>> serverList = service.list().stream()
            .map(s -> toServerMap(s, appByServerId.get(s.getServerId())))
            .toList();
        return ResponseEntity.ok(Map.of("servers", serverList));
    }

    private Map<String, Object> toServerMap(McpServerRecord s, McpAppEntity app) {
        List<Map<String, Object>> tools = s.getTools() == null ? List.of() :
            s.getTools().stream().map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", t.getName());
                m.put("description", t.getDescription());
                m.put("inputSchema", t.getInputSchema() != null ? t.getInputSchema() : Map.of());
                return m;
            }).toList();

        List<Map<String, Object>> resources = s.getResources() == null ? List.of() :
            s.getResources().stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("uri", r.getUri());
                m.put("name", r.getName());
                m.put("description", r.getDescription() != null ? r.getDescription() : "");
                m.put("mimeType", r.getMimeType() != null ? r.getMimeType() : "");
                return m;
            }).toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getServerId());
        m.put("name", s.getName());
        m.put("url", s.getUrl());
        m.put("description", s.getDescription() != null ? s.getDescription() : "");
        m.put("status", s.getStatus());
        m.put("tools", tools);
        m.put("resources", resources);
        m.put("registeredAt", s.getRegisteredAt().toString());
        m.put("displayName", app != null ? app.getDisplayName() : null);
        m.put("thumbnail", app != null ? app.getThumbnail() : null);
        m.put("credit", app != null ? app.getCredit() : 0);
        m.put("appDescription", app != null ? app.getDescription() : null);
        m.put("isVisible", app != null && app.isVisible());
        m.put("type", s.getType() != null ? s.getType().name() : "MCP");
        return m;
    }

    /** 특정 서버에 tools/list · resources/list · ping 을 실시간으로 호출해 결과 반환 */
    @GetMapping("/{serverId}/probe")
    public ResponseEntity<JsonNode> probe(
            @PathVariable String serverId,
            @RequestParam String method) throws Exception {

        McpServerRecord server = registry.find(serverId)
            .orElseThrow(ServerNotFoundException::new);

        Map<String, Object> req = Map.of(
            "jsonrpc", "2.0", "id", 1, "method", method, "params", Map.of()
        );
        String body = restClient.post()
            .uri(server.getUrl() + "/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(req))
            .retrieve()
            .body(String.class);

        return ResponseEntity.ok(objectMapper.readTree(body));
    }

    @Data
    public static class RegisterRequest {
        private String url;
        private String name;
        private String webAppUrl;
    }
}
