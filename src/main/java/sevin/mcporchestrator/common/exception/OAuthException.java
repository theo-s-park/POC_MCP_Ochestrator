package sevin.mcporchestrator.common.exception;

public class OAuthException extends McpException {
    public OAuthException(ErrorCode errorCode) {
        super(errorCode);
    }
}
