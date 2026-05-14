package sevin.mcporchestrator.registry;

import sevin.mcporchestrator.registry.domain.CollectResult;

public interface McpCapabilityCollector {
    String method();
    boolean isRequired();
    CollectResult collect(String serverId, String serverUrl);
}
