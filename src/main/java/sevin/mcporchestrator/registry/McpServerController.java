package sevin.mcporchestrator.registry;

import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sevin.mcporchestrator.app.McpAppEntity;
import sevin.mcporchestrator.app.McpAppRepository;
import sevin.mcporchestrator.registry.domain.McpServerRecord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mcp/servers")
public class McpServerController {

    private final McpServerService service;
    private final McpAppRepository mcpAppRepository;

    public McpServerController(McpServerService service, McpAppRepository mcpAppRepository) {
        this.service = service;
        this.mcpAppRepository = mcpAppRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        McpServerRecord record = service.register(
            request.getName(), request.getUrl(),
            request.getDescription(), request.getVersion()
        );
        return ResponseEntity.ok(Map.of(
            "serverId", record.getServerId(),
            "status", record.getStatus()
        ));
    }

    @DeleteMapping("/{serverId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String serverId) {
        if (!service.delete(serverId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
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
        return m;
    }

    @Data
    public static class RegisterRequest {
        private String name;
        private String url;
        private String description;
        private String version;
    }
}
