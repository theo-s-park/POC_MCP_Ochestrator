package sevin.mcporchestrator.agent;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
