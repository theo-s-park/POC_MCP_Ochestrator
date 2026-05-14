package sevin.mcporchestrator.support;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Profile("test")
@RestController
@RequestMapping("/test-mcp")
public class TestMcpServerController {

    private final TestMcpServerState state;

    public TestMcpServerController(TestMcpServerState state) {
        this.state = state;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        if (!state.isHealthy()) {
            return ResponseEntity.status(503).body(Map.of("status", "down"));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/mcp")
    public ResponseEntity<Map<String, Object>> handleMcp(@RequestBody Map<String, Object> body) {
        String method = (String) body.get("method");
        Object id = body.get("id");

        if ("tools/list".equals(method)) {
            List<Map<String, Object>> tools = state.getToolNames().stream()
                .map(name -> Map.<String, Object>of(
                    "name", name,
                    "description", "A test tool: " + name,
                    "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "input", Map.of("type", "string", "description", "Input value")
                        ),
                        "required", List.of("input")
                    )
                ))
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "jsonrpc", "2.0", "id", id,
                "result", Map.of("tools", tools)
            ));
        }

        if ("resources/list".equals(method)) {
            return ResponseEntity.ok(Map.of(
                "jsonrpc", "2.0", "id", id,
                "result", Map.of("resources", List.of(
                    Map.of(
                        "uri",      "image://test-logo.png",
                        "name",     "Test Logo",
                        "mimeType", "image/png"
                    )
                ))
            ));
        }

        if ("resources/read".equals(method)) {
            Map<String, Object> params = (Map<String, Object>) body.get("params");
            String uri = (String) params.get("uri");
            String blob = Base64.getEncoder().encodeToString(
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            return ResponseEntity.ok(Map.of(
                "jsonrpc", "2.0", "id", id,
                "result", Map.of("contents", List.of(
                    Map.of("uri", uri, "mimeType", "image/png", "blob", blob)
                ))
            ));
        }

        return ResponseEntity.ok(Map.of(
            "jsonrpc", "2.0", "id", id,
            "error", Map.of("code", -32601, "message", "Method not found")
        ));
    }
}
