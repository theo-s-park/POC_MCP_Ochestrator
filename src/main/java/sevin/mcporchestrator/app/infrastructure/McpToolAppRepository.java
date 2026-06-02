package sevin.mcporchestrator.app.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import sevin.mcporchestrator.app.domain.McpToolAppEntity;

import java.util.List;
import java.util.Optional;

public interface McpToolAppRepository extends JpaRepository<McpToolAppEntity, String> {
    List<McpToolAppEntity> findByMcpAppId(String mcpAppId);
    Optional<McpToolAppEntity> findByMcpAppIdAndToolName(String mcpAppId, String toolName);
    void deleteByMcpAppId(String mcpAppId);
}
