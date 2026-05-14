package sevin.mcporchestrator.support;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Profile("test")
@Component
public class TestMcpServerState {

    private volatile boolean healthy = true;
    private volatile List<String> toolNames = List.of("test_tool");

    public boolean isHealthy() { return healthy; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }
    public List<String> getToolNames() { return toolNames; }
    public void setToolNames(List<String> toolNames) { this.toolNames = toolNames; }

    public void reset() {
        this.healthy = true;
        this.toolNames = List.of("test_tool");
    }
}
