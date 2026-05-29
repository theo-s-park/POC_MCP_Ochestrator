package sevin.mcporchestrator.server.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.server.application.McpServerRecord;
import sevin.mcporchestrator.server.domain.ServerStatus;
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
            pingMcp(server);
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
        log.info("[HealthCheck] → POST {}/mcp ping", server.getUrl());
        var req = java.util.Map.of("jsonrpc", "2.0", "id", 1, "method", "ping", "params", java.util.Map.of());
        String body = restClient.post()
            .uri(server.getUrl() + "/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(req))
            .retrieve()
            .body(String.class);
        tools.jackson.databind.JsonNode root = objectMapper.readTree(body);
        if (root == null || (!root.has("result") && !root.has("error")))
            throw new RuntimeException("invalid MCP ping response: " + body);
    }

}
