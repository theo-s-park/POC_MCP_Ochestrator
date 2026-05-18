package sevin.mcporchestrator.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // App
    APP_NOT_FOUND(HttpStatus.NOT_FOUND, "APP_001", "앱을 찾을 수 없습니다."),

    // Server
    SERVER_NOT_FOUND(HttpStatus.NOT_FOUND, "SERVER_001", "MCP 서버를 찾을 수 없습니다."),

    // Resource
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_001", "리소스를 찾을 수 없습니다."),
    RESOURCE_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "RESOURCE_002", "MCP 서버에서 리소스를 가져오는 데 실패했습니다."),

    // Auth
    OAUTH_EXCHANGE_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_001", "OAuth 토큰 교환에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode()       { return code; }
    public String getMessage()    { return message; }
}
