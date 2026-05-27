package sevin.mcporchestrator.app.exception;

import sevin.mcporchestrator.common.exception.ErrorCode;
import sevin.mcporchestrator.common.exception.McpException;

public class AppNotFoundException extends McpException {

    public AppNotFoundException() {
        super(ErrorCode.APP_NOT_FOUND);
    }
}
