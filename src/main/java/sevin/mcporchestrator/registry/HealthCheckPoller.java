package sevin.mcporchestrator.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.registry.domain.McpServerRecord;
import sevin.mcporchestrator.registry.domain.ServerStatus;

@Component
public class HealthCheckPoller {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckPoller.class);
    private static final int MAX_FAILURES = 3;

    private final McpServerRegistry registry;
    private final RestClient restClient;

    public HealthCheckPoller(McpServerRegistry registry) {
        this.registry = registry;
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
            restClient.get()
                .uri(server.getUrl() + "/health")
                .retrieve()
                .toBodilessEntity();

            registry.resetHealthCheckFailure(server.getServerId());
            registry.updateStatus(server.getServerId(), ServerStatus.ACTIVE);
            log.info("[HealthCheck] active: {}", server.getName());

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
}
