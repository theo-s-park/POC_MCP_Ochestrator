package sevin.mcporchestrator.support;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Profile("test")
@RestController
@RequestMapping("/error-mcp")
public class ErrorMcpController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/mcp")
    public ResponseEntity<Map<String, Object>> handleMcp(@RequestBody Map<String, Object> body) {
        return ResponseEntity.internalServerError().body(Map.of("error", "internal error"));
    }
}
