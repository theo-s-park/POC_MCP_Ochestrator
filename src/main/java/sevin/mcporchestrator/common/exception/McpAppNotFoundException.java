package sevin.mcporchestrator.common.exception;

public class McpAppNotFoundException extends McpException {

    public McpAppNotFoundException() {
        super(ErrorCode.APP_NOT_FOUND);
    }
}
