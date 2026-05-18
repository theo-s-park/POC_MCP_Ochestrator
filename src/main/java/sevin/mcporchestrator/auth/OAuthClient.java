package sevin.mcporchestrator.auth;

public interface OAuthClient {
    /**
     * authorToken(일회성, 웹에서 OAuth 서버로부터 발급받음)을
     * accessToken(user 정보 + credit 포함)으로 교환한다.
     */
    String exchangeAccessToken(String authorToken);
}
