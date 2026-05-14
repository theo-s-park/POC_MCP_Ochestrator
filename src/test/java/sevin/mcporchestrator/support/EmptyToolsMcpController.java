package sevin.mcporchestrator.support;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Profile("test")
@RestController
@RequestMapping("/empty-tools-mcp")
public class EmptyToolsMcpController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/mcp")
    public ResponseEntity<Map<String, Object>> handleMcp(@RequestBody Map<String, Object> body) {
        String method = (String) body.get("method");
        Object id = body.get("id");

        if ("tools/list".equals(method)) {
            return ResponseEntity.ok(Map.of(
                "jsonrpc", "2.0", "id", id,
                "result", Map.of("tools", List.of())
            ));
        }

        if ("resources/list".equals(method)) {
            return ResponseEntity.ok(Map.of(
                "jsonrpc", "2.0", "id", id,
                "result", Map.of("resources", List.of())
            ));
        }

        return ResponseEntity.ok(Map.of(
            "jsonrpc", "2.0", "id", id,
            "error", Map.of("code", -32601, "message", "Method not found")
        ));
    }
}
