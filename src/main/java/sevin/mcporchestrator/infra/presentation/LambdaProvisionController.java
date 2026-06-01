package sevin.mcporchestrator.infra.presentation;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sevin.mcporchestrator.infra.application.LambdaProvisionService;
import sevin.mcporchestrator.infra.domain.LambdaCreateRequest;
import sevin.mcporchestrator.infra.domain.LambdaCreateResult;

@Tag(name = "Infra", description = "Lambda 인프라 프로비저닝")
@RestController
@RequestMapping("/api/infra")
public class LambdaProvisionController {

    private final LambdaProvisionService service;

    public LambdaProvisionController(LambdaProvisionService service) {
        this.service = service;
    }

    @PostMapping("/lambda/create")
    public ResponseEntity<LambdaCreateResult> create(@RequestBody CreateRequest req) {
        LambdaCreateResult result = service.create(new LambdaCreateRequest(
            req.getFunctionName(),
            req.getEcrImageUri(),
            req.getMemory() > 0 ? req.getMemory() : 512,
            req.getTimeout() > 0 ? req.getTimeout() : 30
        ));
        return ResponseEntity.ok(result);
    }

    @Data
    public static class CreateRequest {
        private String functionName;
        private String ecrImageUri;
        private int memory = 512;
        private int timeout = 30;
    }
}
