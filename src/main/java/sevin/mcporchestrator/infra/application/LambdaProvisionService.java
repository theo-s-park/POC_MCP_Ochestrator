package sevin.mcporchestrator.infra.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sevin.mcporchestrator.common.exception.ErrorCode;
import sevin.mcporchestrator.common.exception.McpException;
import sevin.mcporchestrator.infra.domain.LambdaCreateRequest;
import sevin.mcporchestrator.infra.domain.LambdaCreateResult;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;

@Service
public class LambdaProvisionService {

    private static final Logger log = LoggerFactory.getLogger(LambdaProvisionService.class);

    private final LambdaClient lambdaClient;
    private final String roleArn;

    public LambdaProvisionService(LambdaClient lambdaClient,
                                  @Value("${aws.lambda.execution-role-arn}") String roleArn) {
        this.lambdaClient = lambdaClient;
        this.roleArn = roleArn;
    }

    public boolean isConfigured() {
        return roleArn != null && !roleArn.isBlank();
    }

    public LambdaCreateResult create(LambdaCreateRequest req) {
        if (!isConfigured()) {
            throw new McpException(ErrorCode.LAMBDA_NOT_CONFIGURED);
        }
        log.info("[Lambda] creating function: {} image={}", req.functionName(), req.ecrImageUri());

        CreateFunctionResponse createResp = lambdaClient.createFunction(
            CreateFunctionRequest.builder()
                .functionName(req.functionName())
                .packageType(PackageType.IMAGE)
                .code(FunctionCode.builder()
                    .imageUri(req.ecrImageUri())
                    .build())
                .role(roleArn)
                .memorySize(req.memory())
                .timeout(req.timeout())
                .build()
        );

        log.info("[Lambda] created: {} arn={}", req.functionName(), createResp.functionArn());

        // Function URL 활성화
        AddPermissionResponse permResp = lambdaClient.addPermission(
            AddPermissionRequest.builder()
                .functionName(req.functionName())
                .statementId("AllowPublicAccess")
                .action("lambda:InvokeFunctionUrl")
                .principal("*")
                .functionUrlAuthType(FunctionUrlAuthType.NONE)
                .build()
        );

        CreateFunctionUrlConfigResponse urlResp = lambdaClient.createFunctionUrlConfig(
            CreateFunctionUrlConfigRequest.builder()
                .functionName(req.functionName())
                .authType(FunctionUrlAuthType.NONE)
                .build()
        );

        log.info("[Lambda] function URL: {}", urlResp.functionUrl());

        return new LambdaCreateResult(
            createResp.functionName(),
            createResp.functionArn(),
            urlResp.functionUrl(),
            req.ecrImageUri(),
            roleArn
        );
    }
}
