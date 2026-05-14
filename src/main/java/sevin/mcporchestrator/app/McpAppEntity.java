package sevin.mcporchestrator.app;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "mcp_app")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpAppEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String mcpServerId;

    private String displayName;
    private String thumbnail;

    @Builder.Default
    private int credit = 0;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    private boolean isVisible = false;

    private Instant createdAt;
}
