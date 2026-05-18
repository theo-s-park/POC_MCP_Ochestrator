package sevin.mcporchestrator.app;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "MCP Apps", description = "앱 메타데이터 조회·수정 (백오피스/퍼블릭)")
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

    @GetMapping("/{id}")
    public ResponseEntity<McpAppPublicView> getOne(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable String id, @RequestBody McpAppUpdateRequest request) {
        service.update(id, request);
        return ResponseEntity.ok().build();
    }
}
