package sevin.mcporchestrator.lambda.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sevin.mcporchestrator.common.exception.McpException;
import sevin.mcporchestrator.lambda.application.LambdaInfraService;
import sevin.mcporchestrator.lambda.domain.LambdaInfraResult;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Tag(name = "Lambda Infra", description = "Lambda 인프라 자동 프로비저닝")
@RestController
@RequestMapping("/api/lambda/infra")
public class LambdaInfraController {

    private static final Logger log = LoggerFactory.getLogger(LambdaInfraController.class);

    private final LambdaInfraService service;
    private final ObjectMapper objectMapper;

    public LambdaInfraController(LambdaInfraService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Lambda 인프라 생성 (SSE 스트리밍)",
        description = "ECR → Lambda → Function URL → S3 → CloudFront 순으로 생성하며 각 단계를 SSE 이벤트로 스트리밍합니다.")
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter create(@RequestBody CreateLambdaInfraRequest request) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10분 타임아웃

        Thread.ofVirtual().start(() -> {
            try {
                LambdaInfraResult result = service.create(
                    request.functionName(),
                    request.runtime(),
                    request.timeout(),
                    request.memorySize(),
                    step -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("step")
                                .data(objectMapper.writeValueAsString(step)));
                        } catch (IOException e) {
                            log.warn("[SSE] step event send failed: {}", e.getMessage());
                        }
                    }
                );

                emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(objectMapper.writeValueAsString(LambdaInfraResponse.from(result))));
                emitter.complete();

            } catch (McpException e) {
                sendError(emitter, e.getMessage());
            } catch (Exception e) {
                log.error("[SSE] unexpected error: {}", e.getMessage(), e);
                sendError(emitter, "내부 오류가 발생했습니다.");
            }
        });

        return emitter;
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                .name("error")
                .data(objectMapper.writeValueAsString(new ErrorPayload(message))));
            emitter.complete();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private record ErrorPayload(String message) {}
}
