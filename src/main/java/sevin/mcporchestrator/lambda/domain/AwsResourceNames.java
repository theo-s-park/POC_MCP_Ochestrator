package sevin.mcporchestrator.lambda.domain;

/**
 * Lambda 인프라 생성 시 사용할 AWS 리소스 이름 규칙.
 * functionName 하나로 ECR / S3 / CloudFront 이름을 일관되게 파생시킨다.
 */
public record AwsResourceNames(String functionName, String accountId, String region) {

    public String ecrRepoName() {
        return "lambda/" + functionName;
    }

    public String ecrImageUri() {
        return accountId + ".dkr.ecr." + region + ".amazonaws.com/" + ecrRepoName() + ":latest";
    }

    /**
     * S3 버킷명은 전역 유일해야 하므로 accountId를 suffix로 붙인다.
     * AWS 규칙: 3-63자, 소문자+숫자+하이픈만 허용.
     */
    public String s3BucketName() {
        String raw = (functionName + "-files-" + accountId)
            .toLowerCase()
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-{2,}", "-");
        return raw.length() > 63 ? raw.substring(0, 63) : raw;
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
