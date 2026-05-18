package sevin.mcporchestrator.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.registry.domain.McpServerRecord;
import sevin.mcporchestrator.registry.domain.ServerStatus;
import sevin.mcporchestrator.registry.domain.ServerType;
import tools.jackson.databind.ObjectMapper;

@Component
public class HealthCheckPoller {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckPoller.class);
    private static final int MAX_FAILURES = 3;

    private final McpServerRegistry registry;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HealthCheckPoller(McpServerRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();
    }

    @Scheduled(fixedDelayString = "${mcp.orchestrator.health-check.interval:300000}")
    public void poll() {
        registry.findAll().forEach(this::checkHealth);
    }

    private void checkHealth(McpServerRecord server) {
        try {
            if (server.getType() == ServerType.MCP) {
                pingMcp(server);
            } else {
                pingHttp(server);
            }
            registry.resetHealthCheckFailure(server.getServerId());
            registry.updateStatus(server.getServerId(), ServerStatus.ACTIVE);
            log.info("[HealthCheck] active: {} ({})", server.getName(), server.getType());

        } catch (Exception e) {
            registry.incrementHealthCheckFailure(server.getServerId());
            int failures = registry.find(server.getServerId())
                .map(McpServerRecord::getHealthCheckFailures)
                .orElse(0);

            if (failures >= MAX_FAILURES) {
                registry.updateStatus(server.getServerId(), ServerStatus.INACTIVE);
                log.warn("[HealthCheck] inactive: {} (failures: {})", server.getName(), failures);
            } else {
                log.warn("[HealthCheck] failed #{}: {}", failures, server.getName());
            }
        }
    }

    private void pingMcp(McpServerRecord server) throws Exception {
        var req = java.util.Map.of("jsonrpc", "2.0", "id", 1, "method", "ping", "params", java.util.Map.of());
        restClient.post()
            .uri(server.getUrl() + "/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(req))
            .retrieve()
            .toBodilessEntity();
    }

    private void pingHttp(McpServerRecord server) {
        restClient.get()
            .uri(server.getUrl())
            .retrieve()
            .toBodilessEntity();
    }
}
