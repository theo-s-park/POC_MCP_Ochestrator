package sevin.mcporchestrator.app;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface McpAppRepository extends JpaRepository<McpAppEntity, String> {
    Optional<McpAppEntity> findByMcpServerId(String mcpServerId);
    void deleteByMcpServerId(String mcpServerId);
}
