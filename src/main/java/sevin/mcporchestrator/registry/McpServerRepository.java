package sevin.mcporchestrator.registry;

import org.springframework.data.jpa.repository.JpaRepository;

public interface McpServerRepository extends JpaRepository<McpServerEntity, String> {
}
