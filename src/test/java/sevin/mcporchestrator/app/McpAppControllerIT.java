package sevin.mcporchestrator.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import sevin.mcporchestrator.registry.McpServerRegistry;
import sevin.mcporchestrator.registry.domain.McpServerRecord;
import sevin.mcporchestrator.registry.domain.ServerStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "spring.ai.openai.api-key=test"
)
@ActiveProfiles("test")
class McpAppControllerIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private McpAppRepository mcpAppRepository;

    @Autowired
    private McpServerRegistry registry;

    private MockMvc mockMvc;
    private String serverId;
    private String appId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        serverId = UUID.randomUUID().toString();
        appId = UUID.randomUUID().toString();

        McpServerRecord server = McpServerRecord.builder()
            .serverId(serverId).name("it-server").url("http://it-server")
            .status(ServerStatus.ACTIVE).registeredAt(Instant.now())
            .healthCheckFailures(0).build();
        registry.register(server);

        McpAppEntity app = McpAppEntity.builder()
            .id(appId).mcpServerId(serverId).credit(0).createdAt(Instant.now()).build();
        mcpAppRepository.save(app);
    }

    @AfterEach
    void tearDown() {
        mcpAppRepository.findById(appId).ifPresent(mcpAppRepository::delete);
        registry.delete(serverId);
    }

    @Test
    void getPublicApps_hiddenApp_notIncluded() throws Exception {
        String body = mockMvc.perform(get("/api/mcp/apps/public"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(appId);
    }

    @Test
    void patchApp_makesVisible_thenAppearsInPublic() throws Exception {
        mockMvc.perform(patch("/api/mcp/apps/" + appId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"My App\",\"credit\":15,\"isVisible\":true}"))
            .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/mcp/apps/public"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(appId);
        assertThat(body).contains("My App");
    }

    @Test
    void getServers_includesAppFields() throws Exception {
        McpAppEntity app = mcpAppRepository.findById(appId).orElseThrow();
        app.setDisplayName("Server App");
        app.setCredit(5);
        app.setVisible(true);
        mcpAppRepository.save(app);

        String body = mockMvc.perform(get("/api/mcp/servers"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Server App");
        assertThat(body).contains(serverId);
    }
}
