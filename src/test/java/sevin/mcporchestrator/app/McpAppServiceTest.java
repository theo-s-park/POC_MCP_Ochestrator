package sevin.mcporchestrator.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sevin.mcporchestrator.registry.McpServerRegistry;
import sevin.mcporchestrator.registry.domain.McpServerRecord;
import sevin.mcporchestrator.registry.domain.ServerStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    void update_serverDescription_delegatesToRegistry() {
        McpAppEntity app = buildApp("app1", "srv1");

        when(mcpAppRepository.findById("app1")).thenReturn(Optional.of(app));
        when(mcpAppRepository.save(any())).thenReturn(app);

        McpAppUpdateRequest req = new McpAppUpdateRequest();
        req.setServerDescription("new desc");

        service.update("app1", req);

        verify(registry).updateDescription("srv1", "new desc");
    }

    private McpAppEntity buildApp(String id, String serverId) {
        return McpAppEntity.builder()
            .id(id).mcpServerId(serverId).credit(0).createdAt(Instant.now()).build();
    }
}
