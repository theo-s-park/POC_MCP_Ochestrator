package sevin.mcporchestrator.registry;

import sevin.mcporchestrator.registry.domain.CollectResult;
import sevin.mcporchestrator.registry.domain.ServerType;

public interface McpCapabilityCollector {
    String method();
    boolean isRequired();
    boolean supports(ServerType type);
    CollectResult collect(String serverId, String serverUrl);
}
