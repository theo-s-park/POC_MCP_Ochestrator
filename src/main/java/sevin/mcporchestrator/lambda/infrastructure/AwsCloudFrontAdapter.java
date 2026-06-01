package sevin.mcporchestrator.lambda.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.AllowedMethods;
import software.amazon.awssdk.services.cloudfront.model.CacheBehavior;
import software.amazon.awssdk.services.cloudfront.model.CachedMethods;
import software.amazon.awssdk.services.cloudfront.model.CreateDistributionRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateOriginAccessControlRequest;
import software.amazon.awssdk.services.cloudfront.model.DefaultCacheBehavior;
import software.amazon.awssdk.services.cloudfront.model.DistributionConfig;
import software.amazon.awssdk.services.cloudfront.model.ForwardedValues;
import software.amazon.awssdk.services.cloudfront.model.GeoRestriction;
import software.amazon.awssdk.services.cloudfront.model.GeoRestrictionType;
import software.amazon.awssdk.services.cloudfront.model.HttpVersion;
import software.amazon.awssdk.services.cloudfront.model.Method;
import software.amazon.awssdk.services.cloudfront.model.MinimumProtocolVersion;
import software.amazon.awssdk.services.cloudfront.model.Origin;
import software.amazon.awssdk.services.cloudfront.model.OriginAccessControlConfig;
import software.amazon.awssdk.services.cloudfront.model.OriginAccessControlOriginTypes;
import software.amazon.awssdk.services.cloudfront.model.OriginAccessControlSigningBehaviors;
import software.amazon.awssdk.services.cloudfront.model.OriginAccessControlSigningProtocols;
import software.amazon.awssdk.services.cloudfront.model.Origins;
import software.amazon.awssdk.services.cloudfront.model.PriceClass;
import software.amazon.awssdk.services.cloudfront.model.Restrictions;
import software.amazon.awssdk.services.cloudfront.model.S3OriginConfig;
import software.amazon.awssdk.services.cloudfront.model.ViewerCertificate;
import software.amazon.awssdk.services.cloudfront.model.ViewerProtocolPolicy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;

import java.util.UUID;

@Component
public class AwsCloudFrontAdapter {

    private static final Logger log = LoggerFactory.getLogger(AwsCloudFrontAdapter.class);

    private final CloudFrontClient cloudFrontClient;
    private final S3Client s3Client;

    public AwsCloudFrontAdapter(CloudFrontClient cloudFrontClient, S3Client s3Client) {
        this.cloudFrontClient = cloudFrontClient;
        this.s3Client = s3Client;
    }

    /**
     * S3 버킷 앞에 CloudFront 배포를 생성한다.
     * OAC(Origin Access Control)로 S3 직접 접근을 차단하고 CloudFront를 통해서만 접근 가능하게 설정한다.
     *
     * @return CloudFront 도메인명 (e.g. d1234abcd.cloudfront.net)
     */
    public String createDistribution(String bucketName, String functionName) {
        String oacId = createOriginAccessControl(functionName);
        String s3OriginDomain = bucketName + ".s3.amazonaws.com";
        String originId = "S3-" + bucketName;

        var response = cloudFrontClient.createDistribution(CreateDistributionRequest.builder()
            .distributionConfig(DistributionConfig.builder()
                .callerReference(UUID.randomUUID().toString())
                .comment(functionName + " files")
                .enabled(true)
                .httpVersion(HttpVersion.HTTP2)
                .priceClass(PriceClass.PRICE_CLASS_ALL)
                .origins(Origins.builder()
                    .quantity(1)
                    .items(Origin.builder()
                        .id(originId)
                        .domainName(s3OriginDomain)
                        .originAccessControlId(oacId)
                        .s3OriginConfig(S3OriginConfig.builder()
                            .originAccessIdentity("")
                            .build())
                        .build())
                    .build())
                .defaultCacheBehavior(DefaultCacheBehavior.builder()
                    .targetOriginId(originId)
                    .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                    .allowedMethods(AllowedMethods.builder()
                        .quantity(2)
                        .items(Method.GET, Method.HEAD)
                        .cachedMethods(CachedMethods.builder()
                            .quantity(2)
                            .items(Method.GET, Method.HEAD)
                            .build())
                        .build())
                    .forwardedValues(ForwardedValues.builder()
                        .queryString(false)
                        .cookies(c -> c.forward("none"))
                        .build())
                    .minTTL(0L)
                    .defaultTTL(86400L)
                    .maxTTL(31536000L)
                    .compress(true)
                    .build())
                .restrictions(Restrictions.builder()
                    .geoRestriction(GeoRestriction.builder()
                        .restrictionType(GeoRestrictionType.NONE)
                        .quantity(0)
                        .build())
                    .build())
                .viewerCertificate(ViewerCertificate.builder()
                    .cloudFrontDefaultCertificate(true)
                    .minimumProtocolVersion(MinimumProtocolVersion.TLS_V1_2_2021)
                    .build())
                .build())
            .build());

        String distributionId = response.distribution().id();
        String domainName = response.distribution().domainName();
        log.info("[CloudFront] created distribution: {} → {}", distributionId, domainName);

        // OAC가 S3에 접근할 수 있도록 버킷 정책 추가
        attachS3BucketPolicy(bucketName, distributionId);

        return domainName;
    }

    private String createOriginAccessControl(String functionName) {
        var response = cloudFrontClient.createOriginAccessControl(
            CreateOriginAccessControlRequest.builder()
                .originAccessControlConfig(OriginAccessControlConfig.builder()
                    .name("oac-" + functionName)
                    .description(functionName + " S3 origin access control")
                    .originAccessControlOriginType(OriginAccessControlOriginTypes.S3)
                    .signingBehavior(OriginAccessControlSigningBehaviors.ALWAYS)
                    .signingProtocol(OriginAccessControlSigningProtocols.SIGV4)
                    .build())
                .build());
        return response.originAccessControl().id();
    }

    private void attachS3BucketPolicy(String bucketName, String distributionId) {
        String policy = """
            {
              "Version": "2012-10-17",
              "Statement": [{
                "Sid": "AllowCloudFrontOAC",
                "Effect": "Allow",
                "Principal": { "Service": "cloudfront.amazonaws.com" },
                "Action": "s3:GetObject",
                "Resource": "arn:aws:s3:::%s/*",
                "Condition": {
                  "StringEquals": {
                    "AWS:SourceArn": "arn:aws:cloudfront::%s:distribution/%s"
                  }
                }
              }]
            }
            """.formatted(bucketName, extractAccountFromBucket(bucketName), distributionId);

        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
            .bucket(bucketName)
            .policy(policy)
            .build());
    }

    private String extractAccountFromBucket(String bucketName) {
        // bucketName = {functionName}-files-{accountId}
        String[] parts = bucketName.split("-");
        return parts[parts.length - 1];
    }
}
