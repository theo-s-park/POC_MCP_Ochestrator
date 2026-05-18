package sevin.mcporchestrator.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sevin.mcporchestrator.common.exception.McpException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(McpException.class)
    public ResponseEntity<ErrorResponse> handleMcpException(McpException e) {
        log.warn("[Exception] {} - {}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity
            .status(e.getErrorCode().getStatus())
            .body(new ErrorResponse(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }
}
