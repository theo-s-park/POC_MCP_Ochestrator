package sevin.mcporchestrator.app.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import sevin.mcporchestrator.app.domain.McpAppEntity;

import java.util.Optional;

public interface McpAppRepository extends JpaRepository<McpAppEntity, String> {
    Optional<McpAppEntity> findByMcpServerId(String mcpServerId);
    void deleteByMcpServerId(String mcpServerId);
}
