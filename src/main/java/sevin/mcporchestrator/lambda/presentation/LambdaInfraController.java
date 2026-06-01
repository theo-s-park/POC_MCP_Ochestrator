package sevin.mcporchestrator.lambda.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Operation(summary = "Lambda 인프라 생성 (SSE)",
        description = "ECR 리포지토리 → placeholder 이미지 push → Lambda → Function URL → S3 → CloudFront를 순서대로 생성합니다.")
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter create(@RequestBody CreateLambdaInfraRequest request) {
        SseEmitter emitter = new SseEmitter(1_800_000L); // 30분

        Thread.ofVirtual().start(() -> {
            try {
                LambdaInfraResult result = service.create(
                    request.functionName(), request.runtime(),
                    request.timeout(), request.memorySize(),
                    step -> sendEvent(emitter, "step", step)
                );
                sendEvent(emitter, "complete", LambdaInfraResponse.from(result));
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

    @Operation(summary = "Lambda 코드 배포 (SSE)",
        description = "ECR 최신 이미지로 Lambda 함수 코드를 업데이트합니다. 이미지 push 후 호출하세요.")
    @PostMapping(value = "/{functionName}/deploy", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter deploy(@PathVariable String functionName,
                             @RequestBody DeployLambdaRequest request) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10분

        Thread.ofVirtual().start(() -> {
            try {
                service.updateCode(functionName, request.imageUri(),
                    step -> sendEvent(emitter, "step", step));
                sendEvent(emitter, "complete", new DeployResult(functionName, request.imageUri()));
                emitter.complete();
            } catch (McpException e) {
                sendError(emitter, e.getMessage());
            } catch (Exception e) {
                log.error("[SSE] deploy error: {}", e.getMessage(), e);
                sendError(emitter, "배포 중 오류가 발생했습니다.");
            }
        });

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                .name(eventName)
                .data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            log.warn("[SSE] send failed: {}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        sendEvent(emitter, "error", new ErrorPayload(message));
        emitter.complete();
    }

    private record ErrorPayload(String message) {}
    private record DeployResult(String functionName, String imageUri) {}
}
