package sevin.mcporchestrator.app;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mcp/apps")
public class McpAppController {

    private final McpAppService service;

    public McpAppController(McpAppService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<McpAppView>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/public")
    public ResponseEntity<List<McpAppPublicView>> listPublic() {
        return ResponseEntity.ok(service.findAllPublic());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable String id, @RequestBody McpAppUpdateRequest request) {
        return service.update(id, request)
            .map(app -> ResponseEntity.ok().<Void>build())
            .orElse(ResponseEntity.notFound().build());
    }
}
