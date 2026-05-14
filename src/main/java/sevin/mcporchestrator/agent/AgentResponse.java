package sevin.mcporchestrator.agent;

import java.util.List;

public record AgentResponse(
        String sessionId,
        String question,
        String answer,
        List<ToolTrace> trace
) {
    public record ToolTrace(
            String toolName,
            String server,
            String args,
            String result
    ) {}
}
