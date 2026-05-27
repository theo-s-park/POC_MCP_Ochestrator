package sevin.mcporchestrator.server.exception;

import sevin.mcporchestrator.common.exception.ErrorCode;
import sevin.mcporchestrator.common.exception.McpException;

public class ServerNotFoundException extends McpException {

    public ServerNotFoundException() {
        super(ErrorCode.SERVER_NOT_FOUND);
    }
}
