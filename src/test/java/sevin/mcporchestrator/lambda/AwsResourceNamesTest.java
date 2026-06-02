package sevin.mcporchestrator.lambda;

import org.junit.jupiter.api.Test;
import sevin.mcporchestrator.lambda.domain.AwsResourceNames;

import static org.assertj.core.api.Assertions.assertThat;

class AwsResourceNamesTest {

    private static final String ACCOUNT_ID = "592624331629";
    private static final String REGION = "ap-northeast-2";

    @Test
    void lambdaFunctionName_suffixedWithLambda() {
        var names = new AwsResourceNames("hwp-converter", ACCOUNT_ID, REGION);
        assertThat(names.lambdaFunctionName()).isEqualTo("hwp-converter-lambda");
    }

    @Test
    void ecrRepoName_suffixedWithEcr() {
        var names = new AwsResourceNames("hwp-converter", ACCOUNT_ID, REGION);
        assertThat(names.ecrRepoName()).isEqualTo("hwp-converter-ecr");
    }

    @Test
    void ecrImageUri_correctlyComposed() {
        var names = new AwsResourceNames("hwp-converter", ACCOUNT_ID, REGION);
        assertThat(names.ecrImageUri())
            .isEqualTo("592624331629.dkr.ecr.ap-northeast-2.amazonaws.com/hwp-converter-ecr:latest");
    }

    @Test
    void s3BucketName_withinLengthLimit() {
        var names = new AwsResourceNames("hwp-converter", ACCOUNT_ID, REGION);
        String bucket = names.s3BucketName();
        assertThat(bucket).hasSizeLessThanOrEqualTo(63);
        assertThat(bucket).matches("[a-z0-9-]+");
        assertThat(bucket).contains(ACCOUNT_ID);
        assertThat(bucket).contains("cf-files");
    }

    @Test
    void s3BucketName_veryLongFunctionName_truncated() {
        String longName = "a".repeat(50);
        var names = new AwsResourceNames(longName, ACCOUNT_ID, REGION);
        assertThat(names.s3BucketName()).hasSizeLessThanOrEqualTo(63);
    }

    @Test
    void s3BucketName_uppercaseAndUnderscoreNormalized() {
        var names = new AwsResourceNames("HWP_Converter", ACCOUNT_ID, REGION);
        String bucket = names.s3BucketName();
        assertThat(bucket).doesNotContainPattern("[A-Z]");
    }

    @Test
    void extractAccountId_fromRoleArn() {
        String roleArn = "arn:aws:iam::592624331629:role/test-mcp-lambda-role";
        assertThat(AwsResourceNames.extractAccountId(roleArn)).isEqualTo("592624331629");
    }

    @Test
    void extractAccountId_emptyArn_returnsEmpty() {
        assertThat(AwsResourceNames.extractAccountId("")).isEmpty();
        assertThat(AwsResourceNames.extractAccountId(null)).isEmpty();
    }
}
