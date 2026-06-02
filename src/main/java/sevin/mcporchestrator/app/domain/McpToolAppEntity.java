package sevin.mcporchestrator.app.domain;

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
@Table(name = "mcp_tool_app")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpToolAppEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String mcpAppId;

    @Column(nullable = false)
    private String toolName;        // MCP 서버 원본 이름

    private String displayName;     // 커스터마이징 가능한 표시 이름

    @Column(columnDefinition = "TEXT")
    private String description;     // 커스터마이징 가능한 설명

    @Column(columnDefinition = "TEXT")
    private String thumbnail;

    private String serviceType;

    @Builder.Default
    private int deductCredit = 0;

    @Builder.Default
    private boolean visible = true;

    private String webUrl;

    private Instant createdAt;
}
