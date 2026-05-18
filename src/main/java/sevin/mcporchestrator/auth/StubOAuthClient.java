package sevin.mcporchestrator.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(OAuthClientImpl.class)
public class StubOAuthClient implements OAuthClient {

    private static final Logger log = LoggerFactory.getLogger(StubOAuthClient.class);

    @Override
    public String exchangeAccessToken(String authorToken) {
        String accessToken = "stub-access." + authorToken;
        log.warn("[OAuth][STUB] stub accessToken 반환 — mcp.oauth.access-token-url 설정 시 실제 OAuth 사용");
        return accessToken;
    }
}
