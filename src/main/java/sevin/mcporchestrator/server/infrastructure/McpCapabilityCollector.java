package sevin.mcporchestrator.server.infrastructure;

import sevin.mcporchestrator.server.domain.ServerType;

public interface McpCapabilityCollector {
    String method();
    boolean isRequired();
    boolean supports(ServerType type);
    CollectResult collect(String serverId, String serverUrl);
}
