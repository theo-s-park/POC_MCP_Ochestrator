package sevin.mcporchestrator.lambda.domain;

/**
 * Lambda 인프라 생성 시 사용할 AWS 리소스 이름 규칙.
 * 모든 리소스에 "test-mcp-" prefix를 붙여 IAM 정책에서 test-mcp-* 와일드카드로 일괄 제어한다.
 *
 * 예) input "aspyn-test" →
 *   Lambda:     test-mcp-aspyn-test
 *   ECR:        test-mcp-aspyn-test
 *   S3:         test-mcp-aspyn-test-{accountId}
 *   CloudFront: description "test-mcp-aspyn-test"
 */
public record AwsResourceNames(String functionName, String accountId, String region) {

    private static final String PREFIX = "test-mcp-";

    public String lambdaFunctionName() {
        return PREFIX + functionName;
    }

    public String ecrRepoName() {
        return PREFIX + functionName;
    }

    public String ecrImageUri() {
        return accountId + ".dkr.ecr." + region + ".amazonaws.com/" + ecrRepoName() + ":latest";
    }

    /**
     * S3 버킷명은 전역 유일해야 하므로 accountId를 suffix로 붙인다.
     * AWS 규칙: 3-63자, 소문자+숫자+하이픈만 허용.
     */
    public String s3BucketName() {
        String raw = (PREFIX + functionName + "-" + accountId)
            .toLowerCase()
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-{2,}", "-");
        return raw.length() > 63 ? raw.substring(0, 63) : raw;
    }

    public String cloudFrontDescription() {
        return PREFIX + functionName;
    }

    /**
     * IAM role ARN (arn:aws:iam::{accountId}:role/...) 에서 accountId를 추출한다.
     */
    public static String extractAccountId(String roleArn) {
        if (roleArn == null || roleArn.isBlank()) return "";
        String[] parts = roleArn.split(":");
        return parts.length > 4 ? parts[4] : "";
    }
}
