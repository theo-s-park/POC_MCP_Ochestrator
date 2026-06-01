package sevin.mcporchestrator.lambda.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;

@Component
public class AwsS3Adapter {

    private static final Logger log = LoggerFactory.getLogger(AwsS3Adapter.class);

    private final S3Client s3Client;
    private final String region;

    public AwsS3Adapter(S3Client s3Client,
                        @Value("${aws.region:ap-northeast-2}") String region) {
        this.s3Client = s3Client;
        this.region = region;
    }

    /**
     * S3 버킷을 생성하고 CloudFront 연동을 위한 초기 설정을 적용한다.
     * 이미 존재하면 그대로 반환한다.
     */
    public String createBucket(String bucketName) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(bucketName)
                .createBucketConfiguration(CreateBucketConfiguration.builder()
                    .locationConstraint(region)
                    .build())
                .build());
            log.info("[S3] created bucket: {}", bucketName);
        } catch (BucketAlreadyOwnedByYouException e) {
            log.info("[S3] bucket already exists: {}", bucketName);
        }

        // CloudFront OAC 사용을 위해 퍼블릭 엑세스는 차단 유지
        s3Client.putPublicAccessBlock(PutPublicAccessBlockRequest.builder()
            .bucket(bucketName)
            .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder()
                .blockPublicAcls(true)
                .ignorePublicAcls(true)
                .blockPublicPolicy(true)
                .restrictPublicBuckets(true)
                .build())
            .build());

        // MCP 툴 결과물 웹 접근을 위한 CORS 설정
        s3Client.putBucketCors(PutBucketCorsRequest.builder()
            .bucket(bucketName)
            .corsConfiguration(CORSConfiguration.builder()
                .corsRules(CORSRule.builder()
                    .allowedMethods("GET", "PUT", "POST")
                    .allowedOrigins("*")
                    .allowedHeaders("*")
                    .maxAgeSeconds(3600)
                    .build())
                .build())
            .build());

        return bucketName;
    }
}
