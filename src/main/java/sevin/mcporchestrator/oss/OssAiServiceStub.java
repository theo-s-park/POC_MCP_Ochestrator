package sevin.mcporchestrator.oss;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * OSS polariscloud.tbAIServiceInfo 테이블 stub.
 * OSS RDS 접근 권한 확보 후 실제 DataSource로 교체.
 */
@Component
public class OssAiServiceStub {

    private static final List<OssAiServiceInfo> DATA = List.of(
            new OssAiServiceInfo(1, 1, 1, 10,  "Basic"),
            new OssAiServiceInfo(2, 2, 1, 50,  "Standard"),
            new OssAiServiceInfo(3, 3, 1, 100, "Premium"),
            new OssAiServiceInfo(4, 4, 0, 200, "Enterprise (inactive)")
    );

    public Optional<OssAiServiceInfo> findActiveByType(int type) {
        return DATA.stream()
                .filter(s -> s.type() == type && s.status() == 1)
                .findFirst();
    }

    public List<OssAiServiceInfo> findAllActive() {
        return DATA.stream()
                .filter(s -> s.status() == 1)
                .toList();
    }
}
