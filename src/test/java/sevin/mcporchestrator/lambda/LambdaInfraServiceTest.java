package sevin.mcporchestrator.lambda;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sevin.mcporchestrator.common.exception.McpException;
import sevin.mcporchestrator.lambda.application.LambdaInfraService;
import sevin.mcporchestrator.lambda.domain.LambdaInfraResult;
import sevin.mcporchestrator.lambda.domain.LambdaRuntime;
import sevin.mcporchestrator.lambda.infrastructure.AwsCloudFrontAdapter;
import sevin.mcporchestrator.lambda.infrastructure.AwsEcrAdapter;
import sevin.mcporchestrator.lambda.infrastructure.AwsLambdaAdapter;
import sevin.mcporchestrator.lambda.infrastructure.AwsS3Adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LambdaInfraServiceTest {

    private static final String ROLE_ARN = "arn:aws:iam::592624331629:role/test-mcp-lambda-role";
    private static final String REGION = "ap-northeast-2";

    @Mock private AwsEcrAdapter ecrAdapter;
    @Mock private AwsLambdaAdapter lambdaAdapter;
    @Mock private AwsS3Adapter s3Adapter;
    @Mock private AwsCloudFrontAdapter cloudFrontAdapter;

    private LambdaInfraService service;

    @BeforeEach
    void setUp() {
        service = new LambdaInfraService(
            ecrAdapter, lambdaAdapter, s3Adapter, cloudFrontAdapter,
            ROLE_ARN, REGION, 30, 512
        );
    }

    @Test
    void create_happyPath_returnsCompleteResult() {
        when(ecrAdapter.createRepository(anyString())).thenReturn("592624331629.dkr.ecr.ap-northeast-2.amazonaws.com/lambda/hwp-converter");
        when(lambdaAdapter.createFunction(anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .thenReturn("arn:aws:lambda:ap-northeast-2:592624331629:function:hwp-converter");
        when(lambdaAdapter.enableFunctionUrl(anyString()))
            .thenReturn("https://abc123.lambda-url.ap-northeast-2.on.aws/");
        when(s3Adapter.createBucket(anyString())).thenReturn("hwp-converter-files-592624331629");
        when(cloudFrontAdapter.createDistribution(anyString(), anyString()))
            .thenReturn("d1234abcd.cloudfront.net");

        LambdaInfraResult result = service.create("hwp-converter", LambdaRuntime.JAVA_21, null, null);

        assertThat(result.functionName()).isEqualTo("hwp-converter");
        assertThat(result.functionArn()).contains("hwp-converter");
        assertThat(result.lambdaUrl()).startsWith("https://");
        assertThat(result.ecrRepoUri()).contains("lambda/hwp-converter");
        assertThat(result.s3BucketName()).isEqualTo("hwp-converter-files-592624331629");
        assertThat(result.cloudFrontDomain()).isEqualTo("d1234abcd.cloudfront.net");
    }

    @Test
    void create_noRoleArn_throwsNotConfigured() {
        var unconfiguredService = new LambdaInfraService(
            ecrAdapter, lambdaAdapter, s3Adapter, cloudFrontAdapter,
            "", REGION, 30, 512
        );
        assertThatThrownBy(() -> unconfiguredService.create("hwp-converter", null, null, null))
            .isInstanceOf(McpException.class)
            .hasMessageContaining("설정되지 않았습니다");
    }

    @Test
    void create_defaultsToJava21WhenRuntimeNull() {
        when(ecrAdapter.createRepository(anyString())).thenReturn("ecr-uri");
        when(lambdaAdapter.createFunction(anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .thenReturn("arn:aws:lambda:...:function:test");
        when(lambdaAdapter.enableFunctionUrl(anyString())).thenReturn("https://url/");
        when(s3Adapter.createBucket(anyString())).thenReturn("bucket");
        when(cloudFrontAdapter.createDistribution(anyString(), anyString())).thenReturn("cf.net");

        service.create("test", null, null, null);

        // null runtime → JAVA_21 base image를 placeholder로 push, Lambda는 private ECR 이미지로 생성
        verify(ecrAdapter).pushPlaceholderImage(anyString(), contains("java:21"));
        verify(lambdaAdapter).createFunction(
            eq("test"),
            contains("lambda/test:latest"),
            anyString(), anyInt(), anyInt()
        );
    }

    @Test
    void create_usesRequestTimeoutOverDefault() {
        when(ecrAdapter.createRepository(anyString())).thenReturn("ecr-uri");
        when(lambdaAdapter.createFunction(anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .thenReturn("arn");
        when(lambdaAdapter.enableFunctionUrl(anyString())).thenReturn("https://url/");
        when(s3Adapter.createBucket(anyString())).thenReturn("bucket");
        when(cloudFrontAdapter.createDistribution(anyString(), anyString())).thenReturn("cf.net");

        service.create("test", LambdaRuntime.PYTHON_312, 60, 1024);

        // PYTHON_312 base image를 placeholder로 push, Lambda는 ECR 이미지 + 지정 timeout/memory로 생성
        verify(ecrAdapter).pushPlaceholderImage(anyString(), contains("python:3.12"));
        verify(lambdaAdapter).createFunction(eq("test"), contains("lambda/test:latest"),
            anyString(), eq(60), eq(1024));
    }

    @Test
    void create_ecrCreatedWithCorrectRepoName() {
        when(ecrAdapter.createRepository("lambda/hwp-converter")).thenReturn("ecr-uri");
        when(lambdaAdapter.createFunction(anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .thenReturn("arn");
        when(lambdaAdapter.enableFunctionUrl(anyString())).thenReturn("https://url/");
        when(s3Adapter.createBucket(anyString())).thenReturn("bucket");
        when(cloudFrontAdapter.createDistribution(anyString(), anyString())).thenReturn("cf.net");

        service.create("hwp-converter", LambdaRuntime.JAVA_21, null, null);

        verify(ecrAdapter).createRepository("lambda/hwp-converter");
    }

    @Test
    void create_awsExceptionWrappedAsLambdaCreateFailed() {
        when(ecrAdapter.createRepository(anyString())).thenThrow(new RuntimeException("AWS error"));

        assertThatThrownBy(() -> service.create("test", null, null, null))
            .isInstanceOf(McpException.class)
            .hasMessageContaining("Lambda 함수 생성에 실패했습니다");
    }
}
