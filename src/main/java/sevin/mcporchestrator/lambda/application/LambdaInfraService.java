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

import java.util.function.Consumer;

@Service
public class LambdaInfraService {

    private static final Logger log = LoggerFactory.getLogger(LambdaInfraService.class);

    private final AwsEcrAdapter ecrAdapter;
    private final AwsLambdaAdapter lambdaAdapter;
    private final AwsS3Adapter s3Adapter;
    private final AwsCloudFrontAdapter cloudFrontAdapter;

    private final String executionRoleArn;
    private final String region;
    private final int timeout;
    private final int memorySize;

    public LambdaInfraService(
        AwsEcrAdapter ecrAdapter,
        AwsLambdaAdapter lambdaAdapter,
        AwsS3Adapter s3Adapter,
        AwsCloudFrontAdapter cloudFrontAdapter,
        @Value("${aws.lambda.execution-role-arn:}") String executionRoleArn,
        @Value("${aws.region:ap-northeast-2}") String region,
        @Value("${aws.lambda.timeout:30}") int timeout,
        @Value("${aws.lambda.memory-size:512}") int memorySize
    ) {
        this.ecrAdapter = ecrAdapter;
        this.lambdaAdapter = lambdaAdapter;
        this.s3Adapter = s3Adapter;
        this.cloudFrontAdapter = cloudFrontAdapter;
        this.executionRoleArn = executionRoleArn;
        this.region = region;
        this.timeout = timeout;
        this.memorySize = memorySize;
    }

    /** 테스트용 — step 이벤트 없이 동기 실행 */
    public LambdaInfraResult create(String functionName, LambdaRuntime runtime,
                                    Integer timeoutOverride, Integer memorySizeOverride) {
        return create(functionName, runtime, timeoutOverride, memorySizeOverride, step -> {});
    }

    /**
     * ECR → Lambda → Function URL → S3 → CloudFront 순으로 생성한다.
     * 각 단계 시작/완료/실패 시 onStep 콜백으로 이벤트를 발행한다.
     *
     * runtime이 null이면 JAVA_21 베이스 이미지를 사용한다.
     * 초기 Lambda는 공개 베이스 이미지로 생성되며, 개발자가 실제 이미지를 ECR에 push한 뒤 교체한다.
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

        log.info("[LambdaInfra] provisioning: {} runtime={} (account={}, region={})",
            functionName, effectiveRuntime, accountId, region);

        // 1. ECR
        String ecrRepoUri = step("ECR", onStep,
            () -> ecrAdapter.createRepository(names.ecrRepoName()));

        // 2. Lambda — private ECR 이미지가 push된 뒤에만 생성 가능하므로 이 단계는 건너뜀
        onStep.accept(new LambdaInfraStep("LAMBDA", "pending",
            "ECR(" + ecrRepoUri + ")에 이미지를 push한 뒤 별도로 생성하세요"));
        onStep.accept(new LambdaInfraStep("URL", "pending",
            "Lambda 생성 완료 후 활성화됩니다"));

        // 3. S3
        String s3BucketName = step("S3", onStep,
            () -> s3Adapter.createBucket(names.s3BucketName()));

        // 4. CloudFront
        String cloudFrontDomain = step("CLOUDFRONT", onStep,
            () -> cloudFrontAdapter.createDistribution(s3BucketName, functionName));

        log.info("[LambdaInfra] provisioning complete (Lambda pending image push): {}", functionName);

        return new LambdaInfraResult(functionName, null, null,
            ecrRepoUri, s3BucketName, cloudFrontDomain);
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
