package sevin.mcporchestrator.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sevin.mcporchestrator.registry.domain.McpServerRecord;
import sevin.mcporchestrator.registry.domain.McpTool;
import sevin.mcporchestrator.registry.domain.ServerStatus;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServerRegistryTest {

    @Mock
    private McpServerRepository repository;

    private McpServerRegistry registry;

    @BeforeEach
    void setUp() {
        when(repository.findAll()).thenReturn(List.of());
        registry = new McpServerRegistry(repository, new ObjectMapper());
        registry.loadFromDb();
    }

    @Test
    void register_storesInMemory() {
        McpServerRecord server = buildServer("s1", "my-server");

        registry.register(server);

        assertThat(registry.find("s1")).isPresent();
        assertThat(registry.find("s1").get().getName()).isEqualTo("my-server");
    }

    @Test
    void delete_removesFromMemory() {
        registry.register(buildServer("s1", "my-server"));

        boolean deleted = registry.delete("s1");

        assertThat(deleted).isTrue();
        assertThat(registry.find("s1")).isEmpty();
    }

    @Test
    void findByToolName_returnsMatchingServer() {
        McpTool tool = new McpTool();
        tool.setName("resize_image");
        tool.setDescription("resize");

        McpServerRecord server = buildServer("s1", "tool-server");
        server.setTools(List.of(tool));
        registry.register(server);

        assertThat(registry.findByToolName("resize_image")).isPresent();
        assertThat(registry.findByToolName("resize_image").get().getServerId()).isEqualTo("s1");
        assertThat(registry.findByToolName("unknown_tool")).isEmpty();
    }

    @Test
    void updateDescription_changesInMemory() {
        registry.register(buildServer("s1", "my-server"));

        registry.updateDescription("s1", "updated description");

        assertThat(registry.find("s1").get().getDescription()).isEqualTo("updated description");
    }

    private McpServerRecord buildServer(String id, String name) {
        return McpServerRecord.builder()
            .serverId(id).name(name).url("http://" + name)
            .status(ServerStatus.ACTIVE).registeredAt(Instant.now())
            .healthCheckFailures(0).build();
    }
}
