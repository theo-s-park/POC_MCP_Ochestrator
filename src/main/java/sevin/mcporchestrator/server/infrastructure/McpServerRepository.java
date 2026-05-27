package sevin.mcporchestrator.server.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import sevin.mcporchestrator.server.domain.McpServerEntity;

public interface McpServerRepository extends JpaRepository<McpServerEntity, String> {
}
