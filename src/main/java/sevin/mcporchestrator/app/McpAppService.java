package sevin.mcporchestrator.app;

import org.springframework.stereotype.Service;
import sevin.mcporchestrator.common.exception.McpAppNotFoundException;
import sevin.mcporchestrator.registry.McpServerRegistry;
import sevin.mcporchestrator.registry.domain.McpServerRecord;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class McpAppService {

    private final McpAppRepository mcpAppRepository;
    private final McpServerRegistry registry;

    public McpAppService(McpAppRepository mcpAppRepository, McpServerRegistry registry) {
        this.mcpAppRepository = mcpAppRepository;
        this.registry = registry;
    }

    public List<McpAppView> findAll() {
        Map<String, McpServerRecord> serverMap = registry.findAll().stream()
            .collect(Collectors.toMap(McpServerRecord::getServerId, s -> s));

        return mcpAppRepository.findAll().stream()
            .map(app -> {
                McpServerRecord server = serverMap.get(app.getMcpServerId());
                return McpAppView.of(app, server);
            })
            .toList();
    }

    public List<McpAppPublicView> findAllPublic() {
        Map<String, McpServerRecord> serverMap = registry.findAll().stream()
            .collect(Collectors.toMap(McpServerRecord::getServerId, s -> s));

        return mcpAppRepository.findAll().stream()
            .filter(McpAppEntity::isVisible)
            .map(app -> McpAppPublicView.of(app, serverMap.get(app.getMcpServerId())))
            .toList();
    }

    public McpAppPublicView findById(String id) {
        Map<String, McpServerRecord> serverMap = registry.findAll().stream()
            .collect(Collectors.toMap(McpServerRecord::getServerId, s -> s));

        McpAppEntity app = mcpAppRepository.findById(id)
            .orElseThrow(McpAppNotFoundException::new);
        return McpAppPublicView.of(app, serverMap.get(app.getMcpServerId()));
    }

    public McpAppEntity update(String id, McpAppUpdateRequest request) {
        McpAppEntity app = mcpAppRepository.findById(id)
            .orElseThrow(McpAppNotFoundException::new);
        if (request.getDisplayName() != null) app.setDisplayName(request.getDisplayName());
        if (request.getThumbnail() != null) app.setThumbnail(request.getThumbnail());
        if (request.getCredit() != null) app.setCredit(request.getCredit());
        if (request.getDescription() != null) app.setDescription(request.getDescription());
        if (request.getIsVisible() != null) app.setVisible(request.getIsVisible());
        return mcpAppRepository.save(app);
    }
}
