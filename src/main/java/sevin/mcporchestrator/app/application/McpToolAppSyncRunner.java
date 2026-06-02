package sevin.mcporchestrator.app.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import sevin.mcporchestrator.app.domain.McpAppEntity;
import sevin.mcporchestrator.app.infrastructure.McpAppRepository;
import sevin.mcporchestrator.app.infrastructure.McpToolAppRepository;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.domain.ServerStatus;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;

import java.util.List;

@Component
public class McpToolAppSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpToolAppSyncRunner.class);

    private final McpAppRepository mcpAppRepository;
    private final McpToolAppRepository mcpToolAppRepository;
    private final McpServerRegistry registry;
    private final McpAppService mcpAppService;

    public McpToolAppSyncRunner(McpAppRepository mcpAppRepository,
                                McpToolAppRepository mcpToolAppRepository,
                                McpServerRegistry registry,
                                McpAppService mcpAppService) {
        this.mcpAppRepository = mcpAppRepository;
        this.mcpToolAppRepository = mcpToolAppRepository;
        this.registry = registry;
        this.mcpAppService = mcpAppService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<McpAppEntity> apps = mcpAppRepository.findAll();
        int synced = 0;
        for (McpAppEntity app : apps) {
            List<?> existing = mcpToolAppRepository.findByMcpAppId(app.getId());
            if (!existing.isEmpty()) continue;

            McpServerRecord server = registry.find(app.getMcpServerId()).orElse(null);
            if (server == null || server.getStatus() != ServerStatus.ACTIVE) continue;
            if (server.getTools() == null || server.getTools().isEmpty()) continue;

            mcpAppService.syncToolApps(app.getId(), server.getTools());
            synced++;
        }
        if (synced > 0) {
            log.info("[ToolAppSync] {} app(s) McpToolApp 동기화 완료", synced);
        }
    }
}
