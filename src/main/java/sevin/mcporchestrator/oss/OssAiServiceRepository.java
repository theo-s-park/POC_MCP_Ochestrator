package sevin.mcporchestrator.oss;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OssAiServiceRepository {

    private static final Logger log = LoggerFactory.getLogger(OssAiServiceRepository.class);

    private static final List<OssAiServiceInfo> STUB = List.of(
            new OssAiServiceInfo("2",  "ON", 2,  "AI WRITE_GPT-3.5", null),
            new OssAiServiceInfo("3",  "ON", 10, "AI TEMPLATE_CLOVA X", null),
            new OssAiServiceInfo("18", "ON", 0,  "NOVA_AI Chat_Clova X", null)
    );

    @Value("${oss.datasource.url:}")
    private String ossDbUrl;

    @Value("${oss.datasource.username:}")
    private String ossDbUsername;

    @Value("${oss.datasource.password:}")
    private String ossDbPassword;

    private JdbcTemplate ossJdbc;

    @PostConstruct
    public void init() {
        if (ossDbUrl != null && !ossDbUrl.isBlank()) {
            ossJdbc = new JdbcTemplate(
                DataSourceBuilder.create()
                    .url(ossDbUrl)
                    .username(ossDbUsername)
                    .password(ossDbPassword)
                    .driverClassName("com.mysql.cj.jdbc.Driver")
                    .build()
            );
            log.info("[OSS] RDS configured: {}", ossDbUrl);
        } else {
            log.warn("[OSS] OSS_DB_URL not set — stub fallback will be used");
        }
    }

    public List<OssAiServiceInfo> findAllActive() {
        if (ossJdbc != null) {
            try {
                List<OssAiServiceInfo> result = ossJdbc.query(
                    "SELECT type, deductCredit, ServiceDesc FROM tbAIServiceInfo WHERE status = 0",
                    (rs, row) -> new OssAiServiceInfo(
                        String.valueOf(rs.getInt("type")),
                        "ON",
                        rs.getInt("deductCredit"),
                        rs.getString("ServiceDesc"),
                        null
                    )
                );
                log.debug("[OSS] RDS returned {} service types", result.size());
                return result;
            } catch (Exception e) {
                log.warn("[OSS] RDS query failed, falling back to stub: {}", e.getMessage());
            }
        }

        log.warn("[OSS] no RDS configured — returning stub data");
        return STUB;
    }

    public Optional<OssAiServiceInfo> findActiveByServiceType(String serviceType) {
        return findAllActive().stream()
                .filter(s -> serviceType.equals(s.serviceType()))
                .findFirst();
    }
}
