package sevin.mcporchestrator.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.registry.McpServerRegistry;
import sevin.mcporchestrator.registry.domain.McpTool;
import sevin.mcporchestrator.registry.domain.ServerStatus;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient chatClient;
    private final McpServerRegistry registry;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AgentService(ChatClient.Builder chatClientBuilder, McpServerRegistry registry, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
                .build();
    }

    public AgentResponse chat(String question) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        List<AgentResponse.ToolTrace> trace = new ArrayList<>();
        List<ToolCallback> tools = buildToolCallbacks(sessionId, trace);

        log.info("[Agent][{}] question: \"{}\"", sessionId, question);
        log.info("[Agent][{}] available tools: {} -> {}",
                sessionId, tools.size(),
                tools.stream().map(t -> t.getToolDefinition().name()).toList());

        String answer;
        if (tools.isEmpty()) {
            answer = chatClient.prompt().user(question).call().content();
        } else {
            answer = chatClient.prompt()
                    .user(question)
                    .toolCallbacks(tools.toArray(ToolCallback[]::new))
                    .call()
                    .content();
        }

        log.info("[Agent][{}] answer: \"{}\"", sessionId, answer);
        return new AgentResponse(sessionId, question, answer, trace);
    }

    private List<ToolCallback> buildToolCallbacks(String sessionId, List<AgentResponse.ToolTrace> trace) {
        Set<String> seen = new HashSet<>();
        return registry.findAll().stream()
                .filter(s -> s.getStatus() == ServerStatus.ACTIVE && s.getTools() != null)
                .flatMap(s -> s.getTools().stream())
                .filter(tool -> seen.add(tool.getName()))
                .map(tool -> toCallback(tool, sessionId, trace))
                .toList();
    }

    private ToolCallback toCallback(McpTool tool, String sessionId, List<AgentResponse.ToolTrace> trace) {
        return new McpToolCallback(tool, registry, restClient, objectMapper, sessionId, trace);
    }
}
