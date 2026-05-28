package sevin.mcporchestrator.oss;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "OSS AI Service", description = "OSS tbAIServiceInfo 조회")
@RestController
public class OssAiServiceController {

    private final OssAiServiceRepository ossRepo;

    public OssAiServiceController(OssAiServiceRepository ossRepo) {
        this.ossRepo = ossRepo;
    }

    @GetMapping("/api/oss/service-types")
    public List<OssAiServiceInfo> listActiveTypes() {
        return ossRepo.findAllActive();
    }

    @GetMapping("/api/oss/service-types/detail")
    public OssAiServiceInfo getByServiceType(@RequestParam String serviceType) {
        return ossRepo.findActiveByServiceType(serviceType)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive serviceType: " + serviceType));
    }
}
