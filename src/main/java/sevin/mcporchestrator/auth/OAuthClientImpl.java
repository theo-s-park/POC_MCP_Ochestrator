package sevin.mcporchestrator.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.common.exception.ErrorCode;
import sevin.mcporchestrator.common.exception.OAuthException;

import java.util.Map;

/**
 * mcp.oauth.access-token-url 설정 시 활성화.
 * 요청: POST {url}  Body: { "authorToken": "..." }
 * 응답: { "accessToken": "..." }  — 스펙 확정 후 수정
 */
@Component
@ConditionalOnProperty("mcp.oauth.access-token-url")
public class OAuthClientImpl implements OAuthClient {

    private static final Logger log = LoggerFactory.getLogger(OAuthClientImpl.class);

    private final RestClient restClient;
    private final String accessTokenUrl;

    public OAuthClientImpl(@Value("${mcp.oauth.access-token-url}") String accessTokenUrl) {
        this.accessTokenUrl = accessTokenUrl;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public String exchangeAccessToken(String authorToken) {
        try {
            Map<?, ?> response = restClient.post()
                    .uri(accessTokenUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("authorToken", authorToken))
                    .retrieve()
                    .body(Map.class);

            String accessToken = response != null ? (String) response.get("accessToken") : null;
            if (accessToken == null || accessToken.isBlank()) {
                throw new OAuthException(ErrorCode.OAUTH_EXCHANGE_FAILED);
            }
            log.info("[OAuth] accessToken 교환 완료");
            return accessToken;

        } catch (OAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OAuth] accessToken 교환 실패: {}", e.getMessage());
            throw new OAuthException(ErrorCode.OAUTH_EXCHANGE_FAILED);
        }
    }
}
