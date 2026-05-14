package sevin.mcporchestrator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import sevin.mcporchestrator.app.McpAppEntity;
import sevin.mcporchestrator.app.McpAppRepository;
import sevin.mcporchestrator.registry.HealthCheckPoller;
import sevin.mcporchestrator.registry.McpServerRegistry;
import sevin.mcporchestrator.registry.domain.McpServerRecord;
import sevin.mcporchestrator.registry.domain.ServerStatus;
import sevin.mcporchestrator.support.TestMcpServerState;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.ai.openai.api-key=test"
)
@ActiveProfiles("test")
class McpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private McpServerRegistry registry;

    @Autowired
    private McpAppRepository mcpAppRepository;

    @Autowired
    private HealthCheckPoller healthCheckPoller;

    @Autowired
    private TestMcpServerState testMcpServerState;

    private final HttpClient http = HttpClient.newHttpClient();
    private String serverId;

    @AfterEach
    void tearDown() {
        testMcpServerState.reset();
        if (serverId != null) {
            mcpAppRepository.findByMcpServerId(serverId).ifPresent(mcpAppRepository::delete);
            registry.delete(serverId);
            serverId = null;
        }
    }

    @Test
    void register_collectsToolsAndResources_becomesActive() throws Exception {
        String body = register("test-mcp-1");

        assertThat(body).contains("\"status\":\"ACTIVE\"");

        serverId = extractValue(body, "serverId");

        McpServerRecord server = registry.find(serverId).orElseThrow();
        assertThat(server.getTools()).hasSize(1);
        assertThat(server.getTools().get(0).getName()).isEqualTo("test_tool");
        assertThat(server.getResources()).hasSize(1);
        assertThat(server.getResources().get(0).getUri()).isEqualTo("image://test-logo.png");
        assertThat(server.getResources().get(0).getMimeType()).isEqualTo("image/png");
    }

    @Test
    void register_autoSetsThumbnailFromImageResource() throws Exception {
        String body = register("test-mcp-2");
        serverId = extractValue(body, "serverId");

        McpAppEntity app = mcpAppRepository.findByMcpServerId(serverId).orElseThrow();
        assertThat(app.getThumbnail()).isNotNull();
        assertThat(app.getThumbnail()).contains("/resources/content");
        assertThat(app.getThumbnail()).contains(serverId);
    }

    @Test
    void resourcesProxy_returnsImageBytes() throws Exception {
        String body = register("test-mcp-3");
        serverId = extractValue(body, "serverId");

        String encodedUri = URLEncoder.encode("image://test-logo.png", StandardCharsets.UTF_8);
        String proxyUrl = base() + "/api/mcp/servers/" + serverId + "/resources/content?uri=" + encodedUri;

        HttpResponse<byte[]> response = http.send(
            HttpRequest.newBuilder().uri(URI.create(proxyUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("image/png");
        assertThat(response.body()).isNotEmpty();
    }

    @Test
    void publicApi_afterMakingVisible_returnsAppWithTools() throws Exception {
        String body = register("test-mcp-4");
        serverId = extractValue(body, "serverId");

        McpAppEntity app = mcpAppRepository.findByMcpServerId(serverId).orElseThrow();
        app.setVisible(true);
        app.setDisplayName("Integration Test App");
        mcpAppRepository.save(app);

        HttpResponse<String> publicResponse = http.send(
            HttpRequest.newBuilder().uri(URI.create(base() + "/api/mcp/apps/public")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(publicResponse.statusCode()).isEqualTo(200);
        assertThat(publicResponse.body()).contains("Integration Test App");
        assertThat(publicResponse.body()).contains("test_tool");
        assertThat(publicResponse.body()).contains("input");
    }

    @Test
    void registrationFailed_whenToolsEmpty() throws Exception {
        String json = "{\"name\":\"empty-tools\",\"url\":\"" + base() + "/empty-tools-mcp\"}";
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/mcp/servers/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"REGISTRATION_FAILED\"");

        serverId = extractValue(response.body(), "serverId");
    }

    @Test
    void registrationFailed_whenToolsListErrors() throws Exception {
        String json = "{\"name\":\"error-mcp\",\"url\":\"" + base() + "/error-mcp\"}";
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/mcp/servers/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"REGISTRATION_FAILED\"");

        serverId = extractValue(response.body(), "serverId");
    }

    @Test
    void healthCheck_becomesInactiveAfterConsecutiveFailures() throws Exception {
        String body = register("test-mcp-health-fail");
        serverId = extractValue(body, "serverId");
        assertThat(registry.find(serverId).orElseThrow().getStatus()).isEqualTo(ServerStatus.ACTIVE);

        testMcpServerState.setHealthy(false);
        healthCheckPoller.poll();
        healthCheckPoller.poll();
        healthCheckPoller.poll();

        assertThat(registry.find(serverId).orElseThrow().getStatus()).isEqualTo(ServerStatus.INACTIVE);
    }

    @Test
    void healthCheck_recoversToActiveAfterHealthRestored() throws Exception {
        String body = register("test-mcp-health-recover");
        serverId = extractValue(body, "serverId");

        testMcpServerState.setHealthy(false);
        healthCheckPoller.poll();
        healthCheckPoller.poll();
        healthCheckPoller.poll();
        assertThat(registry.find(serverId).orElseThrow().getStatus()).isEqualTo(ServerStatus.INACTIVE);

        testMcpServerState.setHealthy(true);
        healthCheckPoller.poll();

        assertThat(registry.find(serverId).orElseThrow().getStatus()).isEqualTo(ServerStatus.ACTIVE);
    }

    @Test
    void reregistration_updatesToolsAndReusesServerId() throws Exception {
        String body1 = register("test-mcp-rereg");
        serverId = extractValue(body1, "serverId");

        McpServerRecord before = registry.find(serverId).orElseThrow();
        assertThat(before.getTools()).hasSize(1);
        assertThat(before.getTools().get(0).getName()).isEqualTo("test_tool");

        testMcpServerState.setToolNames(List.of("tool_a", "tool_b"));
        String body2 = register("test-mcp-rereg");
        String serverId2 = extractValue(body2, "serverId");

        assertThat(serverId2).isEqualTo(serverId);
        McpServerRecord after = registry.find(serverId).orElseThrow();
        assertThat(after.getTools()).hasSize(2);
        assertThat(after.getTools().stream().map(t -> t.getName()).toList())
            .containsExactlyInAnyOrder("tool_a", "tool_b");
    }

    private String register(String name) throws IOException, InterruptedException {
        String json = "{\"name\":\"" + name + "\",\"url\":\"" + base() + "/test-mcp\"}";
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/mcp/servers/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private String extractValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search) + search.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
