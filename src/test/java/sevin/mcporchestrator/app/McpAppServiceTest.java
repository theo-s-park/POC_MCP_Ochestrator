package sevin.mcporchestrator.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sevin.mcporchestrator.app.application.McpAppService;
import sevin.mcporchestrator.app.domain.McpAppEntity;
import sevin.mcporchestrator.app.exception.AppNotFoundException;
import sevin.mcporchestrator.app.infrastructure.McpAppRepository;
import sevin.mcporchestrator.app.presentation.McpAppPublicView;
import sevin.mcporchestrator.app.presentation.McpAppUpdateRequest;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.domain.ServerStatus;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class McpAppServiceTest {

    @Mock
    private McpAppRepository mcpAppRepository;

    @Mock
    private McpServerRegistry registry;

    @InjectMocks
    private McpAppService service;

    @Test
    void findAllPublic_returnsOnlyVisibleApps() {
        McpAppEntity visible = buildApp("app1", "srv1");
        visible.setVisible(true);
        McpAppEntity hidden = buildApp("app2", "srv2");

        when(mcpAppRepository.findAll()).thenReturn(List.of(visible, hidden));
        when(registry.findAll()).thenReturn(List.of());

        List<McpAppPublicView> result = service.findAllPublic();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("app1");
    }

    @Test
    void findAllPublic_fallbacksToServerNameWhenDisplayNameNull() {
        McpAppEntity app = buildApp("app1", "srv1");
        app.setVisible(true);

        McpServerRecord server = McpServerRecord.builder()
            .serverId("srv1").name("fallback-name").url("http://test")
            .status(ServerStatus.ACTIVE).registeredAt(Instant.now()).healthCheckFailures(0).build();

        when(mcpAppRepository.findAll()).thenReturn(List.of(app));
        when(registry.findAll()).thenReturn(List.of(server));

        List<McpAppPublicView> result = service.findAllPublic();

        assertThat(result.get(0).displayName()).isEqualTo("fallback-name");
    }

    @Test
    void update_skipsNullFields() {
        McpAppEntity app = buildApp("app1", "srv1");
        app.setDisplayName("original");
        app.setCredit(5);

        when(mcpAppRepository.findById("app1")).thenReturn(Optional.of(app));
        when(mcpAppRepository.save(any())).thenReturn(app);

        McpAppUpdateRequest req = new McpAppUpdateRequest();
        req.setCredit(20);

        service.update("app1", req);

        assertThat(app.getDisplayName()).isEqualTo("original");
        assertThat(app.getCredit()).isEqualTo(20);
    }

    @Test
    void findById_returnsPublicView() {
        McpAppEntity app = buildApp("app1", "srv1");
        app.setDisplayName("HWP 변환기");
        app.setCredit(5);

        McpServerRecord server = McpServerRecord.builder()
            .serverId("srv1").name("hwp-converter").url("http://localhost:8081")
            .status(ServerStatus.ACTIVE).registeredAt(Instant.now()).healthCheckFailures(0).build();

        when(mcpAppRepository.findById("app1")).thenReturn(Optional.of(app));
        when(registry.findAll()).thenReturn(List.of(server));

        McpAppPublicView result = service.findById("app1");

        assertThat(result.id()).isEqualTo("app1");
        assertThat(result.displayName()).isEqualTo("HWP 변환기");
    }

    @Test
    void findById_unknownId_throwsNotFoundException() {
        when(mcpAppRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("unknown"))
            .isInstanceOf(AppNotFoundException.class);
    }

    private McpAppEntity buildApp(String id, String serverId) {
        return McpAppEntity.builder()
            .id(id).mcpServerId(serverId).credit(0).createdAt(Instant.now()).build();
    }
}
