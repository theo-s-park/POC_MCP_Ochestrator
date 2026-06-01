package sevin.mcporchestrator.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sevin.mcporchestrator.common.exception.McpException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(McpException.class)
    public ResponseEntity<ErrorResponse> handleMcpException(McpException e) {
        int status = e.getErrorCode().getStatus().value();
        if (status >= 500) {
            log.error("[Exception] {} - {}", e.getErrorCode().getCode(), e.getMessage(), e);
        } else {
            log.warn("[Exception] {} - {}", e.getErrorCode().getCode(), e.getMessage());
        }
        return ResponseEntity
            .status(e.getErrorCode().getStatus())
            .body(new ErrorResponse(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("[Exception] INVALID_REQUEST - {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_REQUEST", "요청 본문을 읽을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("[Exception] INTERNAL_ERROR - {}", e.getMessage(), e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
