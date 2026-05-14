package sevin.mcporchestrator.app;

import org.springframework.stereotype.Service;
import sevin.mcporchestrator.registry.McpServerRegistry;
import sevin.mcporchestrator.registry.domain.McpServerRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public Optional<McpAppEntity> update(String id, McpAppUpdateRequest request) {
        return mcpAppRepository.findById(id).map(app -> {
            if (request.getDisplayName() != null) app.setDisplayName(request.getDisplayName());
            if (request.getThumbnail() != null) app.setThumbnail(request.getThumbnail());
            if (request.getCredit() != null) app.setCredit(request.getCredit());
            if (request.getDescription() != null) app.setDescription(request.getDescription());
            if (request.getIsVisible() != null) app.setVisible(request.getIsVisible());
            if (request.getServerDescription() != null) registry.updateDescription(app.getMcpServerId(), request.getServerDescription());
            return mcpAppRepository.save(app);
        });
    }
}
