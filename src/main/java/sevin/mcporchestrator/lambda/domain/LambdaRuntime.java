package sevin.mcporchestrator.lambda.domain;

/**
 * Lambda 컨테이너 기반 베이스 이미지 선택.
 * 개발자가 자신의 이미지를 push하기 전까지 이 베이스 이미지로 Lambda를 초기 생성한다.
 */
public enum LambdaRuntime {

    JAVA_21("java21",    "public.ecr.aws/lambda/java:21"),
    JAVA_17("java17",    "public.ecr.aws/lambda/java:17"),
    PYTHON_312("python312", "public.ecr.aws/lambda/python:3.12"),
    PYTHON_311("python311", "public.ecr.aws/lambda/python:3.11"),
    NODEJS_20("nodejs20", "public.ecr.aws/lambda/nodejs:20"),
    NODEJS_18("nodejs18", "public.ecr.aws/lambda/nodejs:18"),
    CUSTOM_AL2023("custom-al2023", "public.ecr.aws/lambda/provided:al2023");

    private final String label;
    private final String baseImageUri;

    LambdaRuntime(String label, String baseImageUri) {
        this.label = label;
        this.baseImageUri = baseImageUri;
    }

    public String getLabel() { return label; }
    public String getBaseImageUri() { return baseImageUri; }
}
