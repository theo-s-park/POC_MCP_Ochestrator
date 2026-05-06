package sevin.mcporchestrator.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient chatClient;

    public AgentService(ChatClient.Builder builder,
                        @Qualifier("randomTools") ToolCallbackProvider mcpTools) {
        this.chatClient = builder
                .defaultToolCallbacks(mcpTools)
                .build();
    }

    public String chat(String question) {
        log.info("[Agent] 질문 수신: {}", question);
        String answer = chatClient.prompt()
                .user(question)
                .call()
                .content();
        log.info("[Agent] 최종 응답: {}", answer);
        return answer;
    }
}
