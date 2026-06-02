package sevin.mcporchestrator.server.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sevin.mcporchestrator.app.infrastructure.McpAppRepository;
import sevin.mcporchestrator.app.domain.McpAppEntity;
import sevin.mcporchestrator.server.exception.ServerNotFoundException;
import sevin.mcporchestrator.server.infrastructure.CollectResult;
import sevin.mcporchestrator.server.infrastructure.McpCapabilityCollector;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;
import sevin.mcporchestrator.server.domain.ServerStatus;
import sevin.mcporchestrator.server.domain.ServerType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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

    public McpServerRecord register(String url, String name, String webAppUrl, ServerType type) {
        return doRegister(url, name, webAppUrl, type, null);
    }

    public McpServerRecord registerWithStream(String url, String name, String webAppUrl, ServerType type,
                                              Consumer<HandshakeStep> stepCallback) {
        return doRegister(url, name, webAppUrl, type, stepCallback);
    }

    private McpServerRecord doRegister(String url, String name, String webAppUrl, ServerType type,
                                       Consumer<HandshakeStep> stepCallback) {
        ServerType resolvedType = type != null ? type : ServerType.MCP;
        Optional<McpServerRecord> existing = registry.findByUrl(url);
        String serverId = existing.map(McpServerRecord::getServerId)
            .orElse(UUID.randomUUID().toString());
        Instant registeredAt = existing.map(McpServerRecord::getRegisteredAt)
            .orElse(Instant.now());

        String resolvedName = (name != null && !name.isBlank()) ? name : deriveNameFromUrl(url);

        // 기존 등록 시 저장된 MCP 키 보존 (Lambda 생성 시 주입된 키가 재등록 시 날아가지 않도록)
        String preservedKey = existing.map(McpServerRecord::getMcpKeyEncrypted).orElse(null);

        McpServerRecord record = McpServerRecord.builder()
            .serverId(serverId)
            .name(resolvedName)
            .url(url)
            .type(resolvedType)
            .status(ServerStatus.PENDING)
            .registeredAt(registeredAt)
            .healthCheckFailures(0)
            .webAppUrl(webAppUrl)
            .mcpKeyEncrypted(preservedKey)
            .build();

        registry.register(record);
        log.info("[Registry] {}: {} ({}) type={}", existing.isPresent() ? "re-registered" : "registered", resolvedName, serverId, resolvedType);

        for (McpCapabilityCollector collector : collectors) {
            if (!collector.supports(resolvedType)) continue;
            emit(stepCallback, collector.method(), "running");
            CollectResult result = collector.collect(serverId, url);
            emit(stepCallback, collector.method(), result == CollectResult.SUCCESS ? "done" : "error");
            if (collector.isRequired() && result != CollectResult.SUCCESS) {
                log.warn("[Registry] required collector {} returned {} - marking REGISTRATION_FAILED: {}",
                    collector.method(), result, serverId);
                registry.updateStatus(serverId, ServerStatus.REGISTRATION_FAILED);
                return registry.find(serverId).orElse(record);
            }
        }

        registry.updateStatus(serverId, ServerStatus.ACTIVE);
        log.info("[Registry] ACTIVE: {} ({})", resolvedName, serverId);

        String autoThumbnail = registry.find(serverId)
            .map(s -> s.getResources() == null ? null :
                s.getResources().stream()
                    .filter(r -> r.getMimeType() != null && r.getMimeType().startsWith("image/"))
                    .findFirst()
                    .map(r -> "/api/mcp/servers/" + serverId + "/resources/content?uri="
                        + URLEncoder.encode(r.getUri(), StandardCharsets.UTF_8))
                    .orElse(null))
            .orElse(null);

        ensureApp(serverId, existing.isPresent(), autoThumbnail);
        return registry.find(serverId).orElse(record);
    }

    private void ensureApp(String serverId, boolean existing, String autoThumbnail) {
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
        log.info("[McpApp] {} for server {} (thumbnail={})", existing ? "updated" : "created", serverId, autoThumbnail);
    }

    private void emit(Consumer<HandshakeStep> cb, String step, String status) {
        if (cb != null) cb.accept(new HandshakeStep(step, status));
    }

    public McpServerRecord refresh(String serverId) {
        McpServerRecord record = registry.find(serverId)
            .orElseThrow(() -> new ServerNotFoundException());

        for (McpCapabilityCollector collector : collectors) {
            if (!collector.supports(record.getType())) continue;
            collector.collect(serverId, record.getUrl());
        }

        log.info("[Registry] refreshed: {} ({})", record.getName(), serverId);
        return registry.find(serverId).orElse(record);
    }

    @Transactional
    public void delete(String serverId) {
        if (!registry.delete(serverId)) {
            throw new ServerNotFoundException();
        }
        mcpAppRepository.deleteByMcpServerId(serverId);
        log.info("[Registry] deleted: {}", serverId);
    }

    public List<McpServerRecord> list() {
        return registry.findAll();
    }

    private String deriveNameFromUrl(String url) {
        try {
            String host = new java.net.URI(url).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }
}
