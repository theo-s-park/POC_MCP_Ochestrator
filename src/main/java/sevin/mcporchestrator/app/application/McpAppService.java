package sevin.mcporchestrator.app.application;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import sevin.mcporchestrator.app.domain.McpAppEntity;
import sevin.mcporchestrator.app.domain.ToolCreditInfo;
import sevin.mcporchestrator.app.exception.AppNotFoundException;
import sevin.mcporchestrator.app.infrastructure.McpAppRepository;
import sevin.mcporchestrator.app.presentation.McpAppPublicView;
import sevin.mcporchestrator.app.presentation.McpAppUpdateRequest;
import sevin.mcporchestrator.app.presentation.McpAppView;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class McpAppService {

    private static final TypeReference<Map<String, ToolCreditInfo>> TOOL_CREDITS_TYPE =
        new TypeReference<>() {};

    private final McpAppRepository mcpAppRepository;
    private final McpServerRegistry registry;
    private final ObjectMapper objectMapper;

    public McpAppService(McpAppRepository mcpAppRepository, McpServerRegistry registry,
                         ObjectMapper objectMapper) {
        this.mcpAppRepository = mcpAppRepository;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public List<McpAppView> findAll() {
        Map<String, McpServerRecord> serverMap = registry.findAll().stream()
            .collect(Collectors.toMap(McpServerRecord::getServerId, s -> s));

        return mcpAppRepository.findAll().stream()
            .map(app -> {
                McpServerRecord server = serverMap.get(app.getMcpServerId());
                return McpAppView.of(app, server, parseToolCredits(app.getToolCreditsJson()));
            })
            .toList();
    }

    public List<McpAppPublicView> findAllPublic() {
        Map<String, McpServerRecord> serverMap = registry.findAll().stream()
            .collect(Collectors.toMap(McpServerRecord::getServerId, s -> s));

        return mcpAppRepository.findAll().stream()
            .filter(McpAppEntity::isVisible)
            .map(app -> McpAppPublicView.of(app, serverMap.get(app.getMcpServerId()),
                parseToolCredits(app.getToolCreditsJson())))
            .toList();
    }

    public McpAppPublicView findById(String id) {
        Map<String, McpServerRecord> serverMap = registry.findAll().stream()
            .collect(Collectors.toMap(McpServerRecord::getServerId, s -> s));

        McpAppEntity app = mcpAppRepository.findById(id)
            .orElseThrow(AppNotFoundException::new);
        return McpAppPublicView.of(app, serverMap.get(app.getMcpServerId()),
            parseToolCredits(app.getToolCreditsJson()));
    }

    public McpAppEntity update(String id, McpAppUpdateRequest request) {
        McpAppEntity app = mcpAppRepository.findById(id)
            .orElseThrow(AppNotFoundException::new);
        if (request.getDisplayName() != null) app.setDisplayName(request.getDisplayName());
        if (request.getThumbnail() != null) app.setThumbnail(request.getThumbnail());
        if (request.getServiceType() != null) app.setServiceType(request.getServiceType());
        if (request.getClientId() != null) app.setClientId(request.getClientId());
        if (request.getRedirectUri() != null) app.setRedirectUri(request.getRedirectUri());
        if (request.getCredit() != null) app.setCredit(request.getCredit());
        if (request.getDescription() != null) app.setDescription(request.getDescription());
        if (request.getIsVisible() != null) app.setVisible(request.getIsVisible());
        if (request.getToolCredits() != null) {
            try {
                app.setToolCreditsJson(objectMapper.writeValueAsString(request.getToolCredits()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize toolCredits", e);
            }
        }
        return mcpAppRepository.save(app);
    }

    private Map<String, ToolCreditInfo> parseToolCredits(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, TOOL_CREDITS_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
