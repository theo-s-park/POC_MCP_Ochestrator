package sevin.mcporchestrator.oss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Component
public class OssApiClient {

    private static final Logger log = LoggerFactory.getLogger(OssApiClient.class);
    private static final String OSS_API_URL = "https://tb-ca-cloud.polarisoffice.com/api/1/ai/service/list";

    private final String cookie;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OssApiClient(
            @Value("${oss.api.cookie:}") String cookie,
            ObjectMapper objectMapper) {
        this.cookie = cookie;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
                .build();
    }

    public boolean isConfigured() {
        return cookie != null && !cookie.isBlank();
    }

    public List<OssAiServiceInfo> fetchAllActive() {
        try {
            String body = restClient.post()
                    .uri(OSS_API_URL)
                    .header("Content-Type", "application/json")
                    .header("Cookie", cookie)
                    .body("{}")
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            if (root.path("resultCode").asInt(-1) != 0) {
                log.warn("[OSS API] resultCode={} msg={}", root.path("resultCode").asInt(), root.path("resultMsg").asText());
                return null;
            }

            List<OssAiServiceInfo> result = new ArrayList<>();
            for (JsonNode info : root.path("infos")) {
                String status = info.path("status").asText();
                if (!"ON".equals(status)) continue;
                result.add(new OssAiServiceInfo(
                        info.path("serviceType").asText(),
                        status,
                        info.path("deductCredit").asInt(0),
                        info.path("serviceDesc").asText(null),
                        info.has("inputLimit") ? info.path("inputLimit").asInt() : null
                ));
            }
            log.info("[OSS API] fetched {} active services", result.size());
            return result;

        } catch (Exception e) {
            log.error("[OSS API] fetch failed: {}", e.getMessage());
            return null;
        }
    }
}
