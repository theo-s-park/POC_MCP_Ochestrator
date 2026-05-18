package sevin.mcporchestrator.common.exception;

public class McpServerNotFoundException extends McpException {

    public McpServerNotFoundException() {
        super(ErrorCode.SERVER_NOT_FOUND);
    }
}
