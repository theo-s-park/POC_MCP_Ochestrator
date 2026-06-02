package sevin.mcporchestrator.app.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sevin.mcporchestrator.app.domain.McpAppEntity;
import sevin.mcporchestrator.app.domain.McpToolAppEntity;
import sevin.mcporchestrator.app.exception.AppNotFoundException;
import sevin.mcporchestrator.app.infrastructure.McpAppRepository;
import sevin.mcporchestrator.app.infrastructure.McpToolAppRepository;
import sevin.mcporchestrator.app.presentation.McpAppPublicView;
import sevin.mcporchestrator.app.presentation.McpAppUpdateRequest;
import sevin.mcporchestrator.app.presentation.McpAppView;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.domain.McpTool;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class McpAppService {

    private final McpAppRepository mcpAppRepository;
    private final McpToolAppRepository mcpToolAppRepository;
    private final McpServerRegistry registry;

    public McpAppService(McpAppRepository mcpAppRepository,
                         McpToolAppRepository mcpToolAppRepository,
                         McpServerRegistry registry) {
        this.mcpAppRepository = mcpAppRepository;
        this.mcpToolAppRepository = mcpToolAppRepository;
        this.registry = registry;
    }

    public List<McpAppView> findAll() {
        Map<String, McpServerRecord> serverMap = serverMap();
        return mcpAppRepository.findAll().stream()
            .map(app -> McpAppView.of(app,
                serverMap.get(app.getMcpServerId()),
                mcpToolAppRepository.findByMcpAppId(app.getId())))
            .toList();
    }

    public List<McpAppPublicView> findAllPublic() {
        Map<String, McpServerRecord> serverMap = serverMap();
        return mcpAppRepository.findAll().stream()
            .filter(McpAppEntity::isVisible)
            .map(app -> McpAppPublicView.of(app,
                serverMap.get(app.getMcpServerId()),
                mcpToolAppRepository.findByMcpAppId(app.getId())))
            .toList();
    }

    public McpAppPublicView findById(String id) {
        McpAppEntity app = mcpAppRepository.findById(id)
            .orElseThrow(AppNotFoundException::new);
        McpServerRecord server = serverMap().get(app.getMcpServerId());
        return McpAppPublicView.of(app, server, mcpToolAppRepository.findByMcpAppId(app.getId()));
    }

    @Transactional
    public McpAppEntity update(String id, McpAppUpdateRequest request) {
        McpAppEntity app = mcpAppRepository.findById(id)
            .orElseThrow(AppNotFoundException::new);

        if (request.getDisplayName() != null) app.setDisplayName(request.getDisplayName());
        if (request.getThumbnail() != null)    app.setThumbnail(request.getThumbnail());
        if (request.getClientId() != null)     app.setClientId(request.getClientId());
        if (request.getRedirectUri() != null)  app.setRedirectUri(request.getRedirectUri());
        if (request.getDescription() != null)  app.setDescription(request.getDescription());
        if (request.getCategory() != null)     app.setCategory(request.getCategory());
        if (request.getIsVisible() != null)    app.setVisible(request.getIsVisible());
        mcpAppRepository.save(app);

        if (request.getTools() != null) {
            request.getTools().forEach((toolName, toolUpdate) ->
                mcpToolAppRepository.findByMcpAppIdAndToolName(id, toolName).ifPresent(toolApp -> {
                    if (toolUpdate.getDisplayName() != null)  toolApp.setDisplayName(toolUpdate.getDisplayName());
                    if (toolUpdate.getDescription() != null)  toolApp.setDescription(toolUpdate.getDescription());
                    if (toolUpdate.getServiceType() != null)  toolApp.setServiceType(toolUpdate.getServiceType());
                    if (toolUpdate.getDeductCredit() != null) toolApp.setDeductCredit(toolUpdate.getDeductCredit());
                    if (toolUpdate.getVisible() != null)      toolApp.setVisible(toolUpdate.getVisible());
                    mcpToolAppRepository.save(toolApp);
                })
            );
        }
        return app;
    }

    /** 서버 등록/갱신 시 tools → McpToolApp 동기화 (기존 커스터마이징 보존) */
    @Transactional
    public void syncToolApps(String mcpAppId, List<McpTool> tools) {
        if (tools == null || tools.isEmpty()) return;

        Map<String, McpToolAppEntity> existing = mcpToolAppRepository.findByMcpAppId(mcpAppId)
            .stream().collect(Collectors.toMap(McpToolAppEntity::getToolName, t -> t));

        for (McpTool tool : tools) {
            McpToolAppEntity toolApp = existing.getOrDefault(tool.getName(),
                McpToolAppEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .mcpAppId(mcpAppId)
                    .toolName(tool.getName())
                    .visible(true)
                    .createdAt(Instant.now())
                    .build());
            // 기존 커스터마이징 없을 때만 원본 값 반영
            if (toolApp.getDescription() == null && tool.getDescription() != null) {
                toolApp.setDescription(tool.getDescription());
            }
            if (toolApp.getWebUrl() == null && tool.getWebUrl() != null) {
                toolApp.setWebUrl(tool.getWebUrl());
            }
            mcpToolAppRepository.save(toolApp);
        }
    }

    private Map<String, McpServerRecord> serverMap() {
        return registry.findAll().stream()
            .collect(Collectors.toMap(McpServerRecord::getServerId, s -> s));
    }
}
