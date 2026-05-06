package sevin.mcporchestrator.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

@Component
public class RandomTool {

    private static final Logger log = LoggerFactory.getLogger(RandomTool.class);

    @Tool(description = "minVal 이상 maxVal 이하의 정수 난수를 반환한다")
    public int random(
            @ToolParam(description = "최솟값 (포함)") int minVal,
            @ToolParam(description = "최댓값 (포함)") int maxVal
    ) {
        log.info("[Tool:random] LLM이 도구 호출 → minVal={}, maxVal={}", minVal, maxVal);
        int result = RandomGenerator.getDefault().nextInt(minVal, maxVal + 1);
        log.info("[Tool:random] 도구 실행 결과 → {}", result);
        return result;
    }
}
