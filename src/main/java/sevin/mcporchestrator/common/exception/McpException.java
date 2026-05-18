package sevin.mcporchestrator.common.exception;

public class McpException extends RuntimeException {

    private final ErrorCode errorCode;

    public McpException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
