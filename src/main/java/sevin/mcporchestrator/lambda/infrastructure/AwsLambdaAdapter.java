package sevin.mcporchestrator.lambda.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.Environment;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.FunctionUrlAuthType;
import software.amazon.awssdk.services.lambda.model.PackageType;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;

import java.util.Map;

import java.time.Duration;
import java.util.UUID;

@Component
public class AwsLambdaAdapter {

    private static final Logger log = LoggerFactory.getLogger(AwsLambdaAdapter.class);

    private final LambdaClient lambdaClient;

    public AwsLambdaAdapter(LambdaClient lambdaClient) {
        this.lambdaClient = lambdaClient;
    }

    /**
     * 컨테이너 이미지 기반 Lambda 함수를 생성하고 ARN을 반환한다.
     */
    public String createFunction(String functionName, String imageUri, String roleArn,
                                 int timeoutSeconds, int memorySizeMb) {
        return createFunction(functionName, imageUri, roleArn, timeoutSeconds, memorySizeMb, Map.of());
    }

    public String createFunction(String functionName, String imageUri, String roleArn,
                                 int timeoutSeconds, int memorySizeMb, Map<String, String> envVars) {
        var response = lambdaClient.createFunction(CreateFunctionRequest.builder()
            .functionName(functionName)
            .packageType(PackageType.IMAGE)
            .code(FunctionCode.builder()
                .imageUri(imageUri)
                .build())
            .role(roleArn)
            .timeout(timeoutSeconds)
            .memorySize(memorySizeMb)
            .environment(envVars.isEmpty() ? null : Environment.builder().variables(envVars).build())
            .build());

        String arn = response.functionArn();
        log.info("[Lambda] created function: {} ({})", functionName, arn);

        // 함수가 Active 상태가 될 때까지 대기 (최대 5분)
        lambdaClient.waiter().waitUntilFunctionActive(
            r -> r.functionName(functionName),
            o -> o.waitTimeout(Duration.ofMinutes(5))
        );

        return arn;
    }

    /**
     * Function URL을 활성화하고 URL을 반환한다.
     * 퍼블릭 접근을 허용하기 위해 lambda:InvokeFunctionUrl 권한도 함께 추가한다.
     */
    public String enableFunctionUrl(String functionName) {
        // 퍼블릭 인증 없이 호출 가능하도록 권한 추가
        lambdaClient.addPermission(AddPermissionRequest.builder()
            .functionName(functionName)
            .statementId("AllowPublicInvoke-" + UUID.randomUUID().toString().substring(0, 8))
            .action("lambda:InvokeFunctionUrl")
            .principal("*")
            .functionUrlAuthType(FunctionUrlAuthType.NONE)
            .build());

        var urlResponse = lambdaClient.createFunctionUrlConfig(CreateFunctionUrlConfigRequest.builder()
            .functionName(functionName)
            .authType(FunctionUrlAuthType.NONE)
            .build());

        String url = urlResponse.functionUrl();
        log.info("[Lambda] function URL enabled: {}", url);
        return url;
    }

    /**
     * Lambda 함수 코드를 ECR 최신 이미지로 업데이트한다.
     * 개발자가 실제 이미지를 ECR에 push한 뒤 호출한다.
     */
    public String updateFunctionCode(String functionName, String imageUri) {
        var response = lambdaClient.updateFunctionCode(UpdateFunctionCodeRequest.builder()
            .functionName(functionName)
            .imageUri(imageUri)
            .build());

        lambdaClient.waiter().waitUntilFunctionUpdated(
            r -> r.functionName(functionName),
            o -> o.waitTimeout(Duration.ofMinutes(5))
        );

        log.info("[Lambda] function code updated: {} → {}", functionName, imageUri);
        return response.functionArn();
    }
}
