package sevin.mcporchestrator.agent;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Agent", description = "LLM Agent 채팅 — MCP tool calling 포함")
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody Map<String, String> body) {
        return agentService.chat(body.get("question"));
    }
}
