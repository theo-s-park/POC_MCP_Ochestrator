package sevin.mcporchestrator.server.domain;

public enum ServerType {

    MCP("/mcp", "tools/list", "resources/list"),
    WEBAPP("/tools", "GET /tools", "GET /resources");

    private final String toolsCallPath;  // tools/call 요청 경로 접미사
    private final String toolsMethod;
    private final String resourcesMethod;

    ServerType(String toolsCallPath, String toolsMethod, String resourcesMethod) {
        this.toolsCallPath = toolsCallPath;
        this.toolsMethod = toolsMethod;
        this.resourcesMethod = resourcesMethod;
    }

    public boolean isMcp() {
        return this == MCP;
    }

    public boolean isWebapp() {
        return this == WEBAPP;
    }
}
