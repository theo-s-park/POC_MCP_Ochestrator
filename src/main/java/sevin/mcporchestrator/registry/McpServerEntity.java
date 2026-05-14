package sevin.mcporchestrator.registry;

import jakarta.persistence.*;
import lombok.*;
import sevin.mcporchestrator.registry.domain.ServerStatus;

import java.time.Instant;

@Entity
@Table(name = "mcp_server")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpServerEntity {

    @Id
    private String serverId;
    private String name;
    private String url;
    private String description;
    private String version;

    @Enumerated(EnumType.STRING)
    private ServerStatus status;

    @Column(columnDefinition = "TEXT")
    private String toolsJson;

    @Column(columnDefinition = "TEXT")
    private String resourcesJson;

    private Instant registeredAt;
    private int healthCheckFailures;
}
