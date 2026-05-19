package sevin.mcporchestrator.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.app.McpAppEntity;
import sevin.mcporchestrator.app.McpAppRepository;
import sevin.mcporchestrator.auth.OAuthClient;
import sevin.mcporchestrator.common.exception.McpAppNotFoundException;
import sevin.mcporchestrator.common.exception.McpServerNotFoundException;
import sevin.mcporchestrator.oss.OssAiServiceStub;
import sevin.mcporchestrator.registry.McpServerRegistry;
import sevin.mcporchestrator.registry.domain.McpServerRecord;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Web Execute", description = "웹 사용자 MCP 도구 직접 실행 및 OAuth 토큰 발급")
@RestController
public class WebExecuteController {

    private static final Logger log = LoggerFactory.getLogger(WebExecuteController.class);

    private final McpAppRepository appRepository;
    private final McpServerRegistry registry;
    private final OAuthClient oAuthClient;
    private final OssAiServiceStub ossAiServiceStub;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public WebExecuteController(McpAppRepository appRepository,
                                McpServerRegistry registry,
                                OAuthClient oAuthClient,
                                OssAiServiceStub ossAiServiceStub,
                                ObjectMapper objectMapper) {
        this.appRepository = appRepository;
        this.registry = registry;
        this.oAuthClient = oAuthClient;
        this.ossAiServiceStub = ossAiServiceStub;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
                .build();
    }

    /**
     * Web Marketplace 직접 실행 엔드포인트.
     * 1. authorToken → accessToken 교환 (OAuth)
     * 2. Bearer 토큰을 헤더에 실어 MCP 서버 tools/call 호출
     * 3. 결과 반환
     */
    @PostMapping("/api/web/execute")
    public WebExecuteResponse execute(@RequestBody WebExecuteRequest req) throws Exception {
        // 1. app → server 조회
        McpAppEntity app = appRepository.findById(req.appId())
                .orElseThrow(McpAppNotFoundException::new);

        McpServerRecord server = registry.find(app.getMcpServerId())
                .orElseThrow(McpServerNotFoundException::new);

        // 2. authorToken → accessToken
        String accessToken = oAuthClient.exchangeAccessToken(req.authorToken());
        log.info("[WebExecute] appId={} tool={} server={}", req.appId(), req.toolName(), server.getName());

        // 3. serviceType이 있으면 OSS credit 정보 조회 후 _credit으로 arguments에 merge
        Map<String, Object> arguments = new HashMap<>(req.arguments() != null ? req.arguments() : Map.of());
        if (req.serviceType() != null) {
            ossAiServiceStub.findActiveByType(req.serviceType()).ifPresent(info -> {
                arguments.put("credit", Map.of(
                        "serviceType", info.type(),
                        "deductCredit", info.deductCredit()
                ));
            });
            log.info("[WebExecute] serviceType={} credit merged into arguments", req.serviceType());
        }

        // 4. MCP 서버 tools/call (Bearer 토큰 포함) — ServerType에 따라 통신 방식 분기
        String responseBody;
        JsonNode result;

        if (server.getType() == sevin.mcporchestrator.registry.domain.ServerType.WEBAPP) {
            Map<String, Object> webReq = new HashMap<>();
            webReq.put("name", req.toolName());
            webReq.put("arguments", arguments);
            responseBody = restClient.post()
                    .uri(server.getUrl() + "/tools/call")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .body(objectMapper.writeValueAsString(webReq))
                    .retrieve()
                    .body(String.class);
            result = objectMapper.readTree(responseBody);
        } else {
            Map<String, Object> params = new HashMap<>();
            params.put("name", req.toolName());
            params.put("arguments", arguments);
            Map<String, Object> mcpReq = new HashMap<>();
            mcpReq.put("jsonrpc", "2.0");
            mcpReq.put("id", 1);
            mcpReq.put("method", "tools/call");
            mcpReq.put("params", params);
            responseBody = restClient.post()
                    .uri(server.getUrl() + "/mcp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .body(objectMapper.writeValueAsString(mcpReq))
                    .retrieve()
                    .body(String.class);
            result = objectMapper.readTree(responseBody).path("result");
        }

        return new WebExecuteResponse(req.appId(), req.toolName(), result, app.getCredit());
    }

    /**
     * 개발용 stub 엔드포인트 — OAuth 서버가 준비되기 전까지 임시 authorToken 발급.
     * 실제 OAuth 서버 연동 후 제거.
     */
    @PostMapping("/api/auth/stub/author-token")
    public Map<String, String> stubAuthorToken(@RequestBody(required = false) Map<String, String> body) {
        String userId = body != null ? body.getOrDefault("userId", "anonymous") : "anonymous";
        String authorToken = "author." + UUID.randomUUID().toString().substring(0, 8) + "." + userId;
        log.warn("[Auth][STUB] authorToken 발급 userId={} — OAuth 서버 연동 후 이 엔드포인트를 제거하세요", userId);
        return Map.of("authorToken", authorToken);
    }
}
