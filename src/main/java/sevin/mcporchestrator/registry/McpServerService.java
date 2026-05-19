package sevin.mcporchestrator.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sevin.mcporchestrator.app.McpAppEntity;
import sevin.mcporchestrator.app.McpAppRepository;
import sevin.mcporchestrator.common.exception.McpServerNotFoundException;
import sevin.mcporchestrator.registry.domain.CollectResult;
import sevin.mcporchestrator.registry.domain.McpServerRecord;
import sevin.mcporchestrator.registry.domain.ServerStatus;
import sevin.mcporchestrator.registry.domain.ServerType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class McpServerService {

    private static final Logger log = LoggerFactory.getLogger(McpServerService.class);

    private final McpServerRegistry registry;
    private final List<McpCapabilityCollector> collectors;
    private final McpAppRepository mcpAppRepository;

    public McpServerService(McpServerRegistry registry, List<McpCapabilityCollector> collectors, McpAppRepository mcpAppRepository) {
        this.registry = registry;
        this.collectors = collectors;
        this.mcpAppRepository = mcpAppRepository;
    }

    public McpServerRecord register(String name, String url, String description, String version, ServerType type) {
        Optional<McpServerRecord> existing = registry.findByUrl(url);
        String serverId = existing.map(McpServerRecord::getServerId)
            .orElse(UUID.randomUUID().toString());
        Instant registeredAt = existing.map(McpServerRecord::getRegisteredAt)
            .orElse(Instant.now());

        McpServerRecord record = McpServerRecord.builder()
            .serverId(serverId)
            .name(name)
            .url(url)
            .description(description)
            .version(version)
            .type(type)
            .status(ServerStatus.PENDING)
            .registeredAt(registeredAt)
            .healthCheckFailures(0)
            .build();

        registry.register(record);
        log.info("[Registry] {}: {} ({}) type={}", existing.isPresent() ? "re-registered" : "registered", name, serverId, type);

        for (McpCapabilityCollector collector : collectors) {
            if (!collector.supports(type)) continue;
            CollectResult result = collector.collect(serverId, url);
            if (collector.isRequired() && result != CollectResult.SUCCESS) {
                log.warn("[Registry] required collector {} returned {} - marking REGISTRATION_FAILED: {}",
                    collector.method(), result, serverId);
                registry.updateStatus(serverId, ServerStatus.REGISTRATION_FAILED);
                return registry.find(serverId).orElse(record);
            }
        }

        registry.updateStatus(serverId, ServerStatus.ACTIVE);
        log.info("[Registry] ACTIVE: {} ({})", name, serverId);

        String autoThumbnail = null;
        Optional<McpServerRecord> activeServer = registry.find(serverId);
        if (activeServer.isPresent() && activeServer.get().getResources() != null) {
            autoThumbnail = activeServer.get().getResources().stream()
                .filter(r -> r.getMimeType() != null && r.getMimeType().startsWith("image/"))
                .findFirst()
                .map(r -> "/api/mcp/servers/" + serverId + "/resources/content?uri="
                    + URLEncoder.encode(r.getUri(), StandardCharsets.UTF_8))
                .orElse(null);
        }

        McpAppEntity app = mcpAppRepository.findByMcpServerId(serverId)
            .orElse(McpAppEntity.builder()
                .id(UUID.randomUUID().toString())
                .mcpServerId(serverId)
                .createdAt(Instant.now())
                .build());
        if (autoThumbnail != null && app.getThumbnail() == null) {
            app.setThumbnail(autoThumbnail);
        }
        mcpAppRepository.save(app);
        log.info("[McpApp] {} for server {} (thumbnail={})",
            existing.isPresent() ? "updated" : "created", serverId, autoThumbnail);

        return registry.find(serverId).orElse(record);
    }

    @Transactional
    public void delete(String serverId) {
        if (!registry.delete(serverId)) {
            throw new McpServerNotFoundException();
        }
        mcpAppRepository.deleteByMcpServerId(serverId);
        log.info("[Registry] deleted: {}", serverId);
    }

    public List<McpServerRecord> list() {
        return registry.findAll();
    }
}
