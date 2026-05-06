package sevin.mcporchestrator;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import sevin.mcporchestrator.tool.RandomTool;

@SpringBootApplication
public class McporchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(McporchestratorApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider randomTools(RandomTool randomTool) {
        return MethodToolCallbackProvider.builder().toolObjects(randomTool).build();
    }
}
