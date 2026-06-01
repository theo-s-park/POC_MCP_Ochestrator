package sevin.mcporchestrator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import sevin.mcporchestrator.app.infrastructure.McpAppRepository;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.domain.ServerStatus;
import sevin.mcporchestrator.server.domain.ServerType;
import sevin.mcporchestrator.server.infrastructure.HealthCheckPoller;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;
import sevin.mcporchestrator.support.TestWebappServerState;

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
class WebappIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private McpServerRegistry registry;

    @Autowired
    private McpAppRepository mcpAppRepository;

    @Autowired
    private HealthCheckPoller healthCheckPoller;

    @Autowired
    private TestWebappServerState state;

    private final HttpClient http = HttpClient.newHttpClient();
    private String serverId;

    @AfterEach
    void tearDown() {
        state.reset();
        if (serverId != null) {
            mcpAppRepository.findByMcpServerId(serverId).ifPresent(mcpAppRepository::delete);
            registry.delete(serverId);
            serverId = null;
        }
    }

    @Test
    void register_webapp_collectsToolsViaGet_becomesActive() throws Exception {
        String body = register("webapp-1");

        assertThat(body).contains("\"status\":\"ACTIVE\"");
        serverId = extractValue(body, "serverId");

        McpServerRecord server = registry.find(serverId).orElseThrow();
        assertThat(server.getType()).isEqualTo(ServerType.WEBAPP);
        assertThat(server.getTools()).hasSize(1);
        assertThat(server.getTools().get(0).getName()).isEqualTo("webapp_tool");
        assertThat(server.getTools().get(0).getWebUrl()).isEqualTo("https://service.polarisoffice.com/webapp_tool");
    }

    @Test
    void register_webapp_emptyTools_registrationFailed() throws Exception {
        state.setToolNames(List.of());
        String body = register("webapp-empty");
        assertThat(body).contains("\"status\":\"REGISTRATION_FAILED\"");
        serverId = extractValue(body, "serverId");
    }

    @Test
    void register_webapp_serverDown_registrationFailed() throws Exception {
        state.setHealthy(false);
        String body = register("webapp-down");
        assertThat(body).contains("\"status\":\"REGISTRATION_FAILED\"");
        serverId = extractValue(body, "serverId");
    }

    @Test
    void probe_webapp_toolsList_usesGetRequest() throws Exception {
        String body = register("webapp-probe");
        serverId = extractValue(body, "serverId");

        HttpResponse<String> probe = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/mcp/servers/" + serverId + "/probe?method=tools%2Flist"))
                .GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(probe.statusCode()).isEqualTo(200);
        assertThat(probe.body()).contains("webapp_tool");
    }

    @Test
    void probe_webapp_resourcesList_usesGetRequest() throws Exception {
        String body = register("webapp-probe-res");
        serverId = extractValue(body, "serverId");

        HttpResponse<String> probe = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/mcp/servers/" + serverId + "/probe?method=resources%2Flist"))
                .GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(probe.statusCode()).isEqualTo(200);
        assertThat(probe.body()).contains("webapp-logo.png");
    }

    @Test
    void resourcesProxy_webapp_returnsContent() throws Exception {
        String body = register("webapp-res-proxy");
        serverId = extractValue(body, "serverId");

        String encodedUri = URLEncoder.encode("image://webapp-logo.png", StandardCharsets.UTF_8);
        HttpResponse<byte[]> response = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/mcp/servers/" + serverId + "/resources/content?uri=" + encodedUri))
                .GET().build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("image/png");
    }

    @Test
    void healthCheck_webapp_usesHttpGet_becomesInactive() throws Exception {
        String body = register("webapp-health");
        serverId = extractValue(body, "serverId");
        assertThat(registry.find(serverId).orElseThrow().getStatus()).isEqualTo(ServerStatus.ACTIVE);

        state.setHealthy(false);
        healthCheckPoller.poll();
        healthCheckPoller.poll();
        healthCheckPoller.poll();

        assertThat(registry.find(serverId).orElseThrow().getStatus()).isEqualTo(ServerStatus.INACTIVE);
    }

    @Test
    void healthCheck_webapp_recoversToActive() throws Exception {
        String body = register("webapp-health-recover");
        serverId = extractValue(body, "serverId");

        state.setHealthy(false);
        healthCheckPoller.poll();
        healthCheckPoller.poll();
        healthCheckPoller.poll();

        state.setHealthy(true);
        healthCheckPoller.poll();

        assertThat(registry.find(serverId).orElseThrow().getStatus()).isEqualTo(ServerStatus.ACTIVE);
    }

    @Test
    void register_noUrl_returns400() throws Exception {
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/mcp/servers/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"WEBAPP\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void probe_unknownServerId_returns404() throws Exception {
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/mcp/servers/non-existent-id/probe?method=ping"))
                .GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(404);
    }

    private String register(String name) throws IOException, InterruptedException {
        String json = "{\"name\":\"" + name + "\",\"url\":\"" + base() + "/test-webapp\",\"type\":\"WEBAPP\"}";
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
