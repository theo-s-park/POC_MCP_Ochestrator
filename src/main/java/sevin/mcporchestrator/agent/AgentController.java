package sevin.mcporchestrator.agent;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody AgentRequest request) {
        return new AgentResponse(agentService.chat(request.question()));
    }

    public record AgentRequest(String question) {}
    public record AgentResponse(String answer) {}
}
