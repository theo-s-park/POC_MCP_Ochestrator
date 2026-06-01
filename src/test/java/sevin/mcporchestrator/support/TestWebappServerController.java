package sevin.mcporchestrator.support;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Profile("test")
@RestController
@RequestMapping("/test-webapp")
public class TestWebappServerController {

    private final TestWebappServerState state;

    public TestWebappServerController(TestWebappServerState state) {
        this.state = state;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        if (!state.isHealthy()) {
            return ResponseEntity.status(503).body(Map.of("status", "down"));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> tools() {
        if (!state.isHealthy()) {
            return ResponseEntity.status(503).body(Map.of("error", "service unavailable"));
        }
        List<Map<String, Object>> tools = state.getToolNames().stream()
            .map(name -> Map.<String, Object>of(
                "name", name,
                "description", "A test webapp tool: " + name,
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of("input", Map.of("type", "string")),
                    "required", List.of("input")
                ),
                "webUrl", "https://service.polarisoffice.com/" + name
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("tools", tools));
    }

    @GetMapping("/resources")
    public ResponseEntity<Map<String, Object>> resources() {
        return ResponseEntity.ok(Map.of("resources", List.of(
            Map.of("uri", "image://webapp-logo.png", "name", "Logo", "mimeType", "image/png")
        )));
    }

    @PostMapping("/tools/call")
    public ResponseEntity<Map<String, Object>> toolsCall(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
            "content", List.of(Map.of("type", "text", "text", "webapp tool result"))
        ));
    }

    @GetMapping("/resources/read")
    public ResponseEntity<Map<String, Object>> resourcesRead(@RequestParam String uri) {
        return ResponseEntity.ok(Map.of(
            "contents", List.of(Map.of("uri", uri, "mimeType", "image/png", "blob", "iVBORw0KGgo="))
        ));
    }
}
