package sevin.mcporchestrator.registry;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sevin.mcporchestrator.registry.domain.McpResource;
import sevin.mcporchestrator.registry.domain.McpServerRecord;
import sevin.mcporchestrator.registry.domain.McpTool;
import sevin.mcporchestrator.registry.domain.ServerStatus;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpServerRegistry.class);

    private final ConcurrentHashMap<String, McpServerRecord> servers = new ConcurrentHashMap<>();

    private final McpServerRepository repository;
    private final ObjectMapper objectMapper;

    public McpServerRegistry(McpServerRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadFromDb() {
        repository.findAll().forEach(entity -> servers.put(entity.getServerId(), fromEntity(entity)));
        log.info("[Registry] loaded {} server(s) from DB", servers.size());
    }

    public void register(McpServerRecord record) {
        servers.put(record.getServerId(), record);
        repository.save(toEntity(record));
    }

    public boolean delete(String serverId) {
        McpServerRecord removed = servers.remove(serverId);
        if (removed != null) {
            repository.deleteById(serverId);
            return true;
        }
        return false;
    }

    public Optional<McpServerRecord> find(String serverId) {
        return Optional.ofNullable(servers.get(serverId));
    }

    public List<McpServerRecord> findAll() {
        return List.copyOf(servers.values());
    }

    public Optional<McpServerRecord> findByUrl(String url) {
        return servers.values().stream()
            .filter(s -> url.equals(s.getUrl()))
            .findFirst();
    }

    public Optional<McpServerRecord> findByToolName(String toolName) {
        return servers.values().stream()
            .filter(s -> s.getTools() != null &&
                s.getTools().stream().anyMatch(t -> t.getName().equals(toolName)))
            .findFirst();
    }

    public void updateTools(String serverId, List<McpTool> tools) {
        servers.computeIfPresent(serverId, (id, record) -> {
            record.setTools(tools);
            repository.save(toEntity(record));
            return record;
        });
    }

    public void updateResources(String serverId, List<McpResource> resources) {
        servers.computeIfPresent(serverId, (id, record) -> {
            record.setResources(resources);
            repository.save(toEntity(record));
            return record;
        });
    }

    public void updateDescription(String serverId, String description) {
        servers.computeIfPresent(serverId, (id, record) -> {
            record.setDescription(description);
            repository.save(toEntity(record));
            return record;
        });
    }

    public void updateStatus(String serverId, ServerStatus status) {
        servers.computeIfPresent(serverId, (id, record) -> {
            record.setStatus(status);
            repository.save(toEntity(record));
            return record;
        });
    }

    public void incrementHealthCheckFailure(String serverId) {
        servers.computeIfPresent(serverId, (id, record) -> {
            record.setHealthCheckFailures(record.getHealthCheckFailures() + 1);
            repository.save(toEntity(record));
            return record;
        });
    }

    public void resetHealthCheckFailure(String serverId) {
        servers.computeIfPresent(serverId, (id, record) -> {
            record.setHealthCheckFailures(0);
            repository.save(toEntity(record));
            return record;
        });
    }

    private McpServerEntity toEntity(McpServerRecord record) {
        return McpServerEntity.builder()
            .serverId(record.getServerId())
            .name(record.getName())
            .url(record.getUrl())
            .description(record.getDescription())
            .version(record.getVersion())
            .status(record.getStatus())
            .toolsJson(toJson(record.getTools()))
            .resourcesJson(toJson(record.getResources()))
            .registeredAt(record.getRegisteredAt())
            .healthCheckFailures(record.getHealthCheckFailures())
            .build();
    }

    private McpServerRecord fromEntity(McpServerEntity entity) {
        return McpServerRecord.builder()
            .serverId(entity.getServerId())
            .name(entity.getName())
            .url(entity.getUrl())
            .description(entity.getDescription())
            .version(entity.getVersion())
            .status(entity.getStatus())
            .tools(fromJson(entity.getToolsJson(), McpTool.class))
            .resources(fromJson(entity.getResourcesJson(), McpResource.class))
            .registeredAt(entity.getRegisteredAt())
            .healthCheckFailures(entity.getHealthCheckFailures())
            .build();
    }

    private String toJson(List<?> list) {
        if (list == null) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("[Registry] 직렬화 실패: {}", e.getMessage());
            return "[]";
        }
    }

    private <T> List<T> fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return new ArrayList<>(objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, type)));
        } catch (Exception e) {
            log.warn("[Registry] 역직렬화 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
