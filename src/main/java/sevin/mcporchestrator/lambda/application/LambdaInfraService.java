package sevin.mcporchestrator.lambda.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sevin.mcporchestrator.common.exception.ErrorCode;
import sevin.mcporchestrator.common.exception.McpException;
import sevin.mcporchestrator.lambda.domain.AwsResourceNames;
import sevin.mcporchestrator.lambda.domain.LambdaInfraResult;
import sevin.mcporchestrator.lambda.domain.LambdaRuntime;
import sevin.mcporchestrator.lambda.infrastructure.AwsCloudFrontAdapter;
import sevin.mcporchestrator.lambda.infrastructure.AwsEcrAdapter;
import sevin.mcporchestrator.lambda.infrastructure.AwsLambdaAdapter;
import sevin.mcporchestrator.lambda.infrastructure.AwsS3Adapter;
import sevin.mcporchestrator.server.application.McpServerService;
import sevin.mcporchestrator.server.infrastructure.McpServerRegistry;

import java.util.Map;
import java.util.function.Consumer;

@Service
public class LambdaInfraService {

    private static final Logger log = LoggerFactory.getLogger(LambdaInfraService.class);

    private final AwsEcrAdapter ecrAdapter;
    private final AwsLambdaAdapter lambdaAdapter;
    private final AwsS3Adapter s3Adapter;
    private final AwsCloudFrontAdapter cloudFrontAdapter;
    private final McpKeyService mcpKeyService;
    private final McpServerRegistry registry;
    private final McpServerService mcpServerService;

    private final String executionRoleArn;
    private final String region;
    private final int timeout;
    private final int memorySize;

    public LambdaInfraService(
        AwsEcrAdapter ecrAdapter,
        AwsLambdaAdapter lambdaAdapter,
        AwsS3Adapter s3Adapter,
        AwsCloudFrontAdapter cloudFrontAdapter,
        McpKeyService mcpKeyService,
        McpServerRegistry registry,
        McpServerService mcpServerService,
        @Value("${aws.lambda.execution-role-arn:}") String executionRoleArn,
        @Value("${aws.region:ap-northeast-2}") String region,
        @Value("${aws.lambda.timeout:30}") int timeout,
        @Value("${aws.lambda.memory-size:512}") int memorySize
    ) {
        this.ecrAdapter = ecrAdapter;
        this.lambdaAdapter = lambdaAdapter;
        this.s3Adapter = s3Adapter;
        this.cloudFrontAdapter = cloudFrontAdapter;
        this.mcpKeyService = mcpKeyService;
        this.registry = registry;
        this.mcpServerService = mcpServerService;
        this.executionRoleArn = executionRoleArn;
        this.region = region;
        this.timeout = timeout;
        this.memorySize = memorySize;
    }

    /** 테스트용 no-op 콜백 */
    public LambdaInfraResult create(String functionName, LambdaRuntime runtime,
                                    Integer timeoutOverride, Integer memorySizeOverride) {
        return create(functionName, runtime, timeoutOverride, memorySizeOverride, step -> {});
    }

    /**
     * 전체 Lambda 인프라를 순서대로 생성한다.
     *
     * 1. API Key 생성 → Lambda env var(MCP_SERVER_KEY) 주입
     * 2. ECR 리포지토리 생성
     * 3. placeholder 이미지 ECR push (Docker)
     * 4. Lambda 함수 생성 (MCP_SERVER_KEY 포함)
     * 5. Function URL 활성화
     * 6. S3 버킷 생성
     * 7. CloudFront 배포 생성
     * 8. DB에 서버 레코드 저장 (PENDING, 키 AES 암호화)
     */
    public LambdaInfraResult create(String functionName, LambdaRuntime runtime,
                                    Integer timeoutOverride, Integer memorySizeOverride,
                                    Consumer<LambdaInfraStep> onStep) {
        if (executionRoleArn == null || executionRoleArn.isBlank()) {
            throw new McpException(ErrorCode.LAMBDA_NOT_CONFIGURED);
        }

        int effectiveTimeout = timeoutOverride != null ? timeoutOverride : timeout;
        int effectiveMemory = memorySizeOverride != null ? memorySizeOverride : memorySize;
        LambdaRuntime effectiveRuntime = runtime != null ? runtime : LambdaRuntime.JAVA_21;

        String accountId = AwsResourceNames.extractAccountId(executionRoleArn);
        AwsResourceNames names = new AwsResourceNames(functionName, accountId, region);
        String lambdaName = names.lambdaFunctionName();  // aspyn-test-lambda
        String ecrImageUri = names.ecrImageUri();

        // API Key 생성 (Lambda env var로 주입, DB엔 암호화해서 저장)
        String mcpKey = mcpKeyService.generateKey();
        String mcpKeyEncrypted = mcpKeyService.encrypt(mcpKey);
        Map<String, String> envVars = Map.of("MCP_SERVER_KEY", mcpKey);

        log.info("[LambdaInfra] provisioning: {} runtime={} (account={}, region={})",
            functionName, effectiveRuntime, accountId, region);

        // 1. ECR 리포지토리 생성
        String ecrRepoUri = step("ECR", onStep,
            () -> ecrAdapter.createRepository(names.ecrRepoName()));

        // 2. placeholder 이미지 push → private ECR에 이미지가 있어야 Lambda 생성 가능
        step("PUSH", onStep, () -> {
            ecrAdapter.pushPlaceholderImage(ecrRepoUri, effectiveRuntime.getBaseImageUri());
            return ecrImageUri + " (placeholder)";
        });

        // 3. Lambda 함수 생성 (MCP_SERVER_KEY env var 포함)
        String functionArn = step("LAMBDA", onStep,
            () -> lambdaAdapter.createFunction(lambdaName, ecrImageUri,
                executionRoleArn, effectiveTimeout, effectiveMemory, envVars));

        // 4. Function URL 활성화
        String lambdaUrl = step("URL", onStep,
            () -> lambdaAdapter.enableFunctionUrl(lambdaName));

        // 5. S3 버킷 생성
        String s3BucketName = step("S3", onStep,
            () -> s3Adapter.createBucket(names.s3BucketName()));

        // 6. CloudFront 배포 생성
        String cloudFrontDomain = step("CLOUDFRONT", onStep,
            () -> cloudFrontAdapter.createDistribution(s3BucketName, names.cloudFrontDescription()));

        // 7. DB에 서버 레코드 선등록 (PENDING, 암호화된 키 포함)
        registry.registerLambdaServer(lambdaName, lambdaUrl, mcpKeyEncrypted);

        log.info("[LambdaInfra] provisioning complete: {}", functionName);

        return new LambdaInfraResult(functionName, functionArn, lambdaUrl,
            ecrRepoUri, s3BucketName, cloudFrontDomain, mcpKey);
    }

    /**
     * Lambda 함수 코드를 ECR 최신 이미지로 업데이트하고 핸드셰이킹을 트리거한다.
     * 개발자가 실제 이미지를 ECR에 push한 뒤 호출한다.
     * 핸드셰이킹(ping → tools/list → resources/list) 시 X-MCP-KEY 헤더가 자동으로 포함된다.
     */
    public void updateCode(String functionName, String imageUri, Consumer<LambdaInfraStep> onStep) {
        step("UPDATE", onStep,
            () -> lambdaAdapter.updateFunctionCode(functionName, imageUri));

        // 코드 업데이트 후 핸드셰이킹 트리거 — Lambda URL로 서버 조회 후 refresh
        step("HANDSHAKE", onStep, () -> {
            registry.findAll().stream()
                .filter(s -> s.getName().equals(functionName))
                .findFirst()
                .ifPresent(s -> {
                    log.info("[LambdaInfra] triggering handshake for {} ({})", functionName, s.getServerId());
                    mcpServerService.refresh(s.getServerId());
                });
            return "핸드셰이킹 완료";
        });
    }

    private String step(String name, Consumer<LambdaInfraStep> onStep, StepAction action) {
        onStep.accept(new LambdaInfraStep(name, "running", null));
        try {
            String value = action.run();
            onStep.accept(new LambdaInfraStep(name, "done", value));
            return value;
        } catch (Exception e) {
            onStep.accept(new LambdaInfraStep(name, "error", e.getMessage()));
            log.error("[LambdaInfra] step {} failed: {}", name, e.getMessage(), e);
            throw new McpException(ErrorCode.LAMBDA_CREATE_FAILED);
        }
    }

    @FunctionalInterface
    private interface StepAction {
        String run() throws Exception;
    }
}
