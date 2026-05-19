package sevin.mcporchestrator.oss;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "OSS AI Service", description = "OSS tbAIServiceInfo 조회 (stub — OSS RDS 연동 전)")
@RestController
public class OssAiServiceController {

    private final OssAiServiceStub stub;

    public OssAiServiceController(OssAiServiceStub stub) {
        this.stub = stub;
    }

    @GetMapping("/api/oss/service-types")
    public List<OssAiServiceInfo> listActiveTypes() {
        return stub.findAllActive();
    }

    @GetMapping("/api/oss/service-types/detail")
    public OssAiServiceInfo getByType(@RequestParam int type) {
        return stub.findActiveByType(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive serviceType: " + type));
    }
}
