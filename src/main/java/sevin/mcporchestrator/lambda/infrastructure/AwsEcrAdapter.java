package sevin.mcporchestrator.lambda.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.CreateRepositoryRequest;
import software.amazon.awssdk.services.ecr.model.DescribeRepositoriesRequest;
import software.amazon.awssdk.services.ecr.model.ImageTagMutability;
import software.amazon.awssdk.services.ecr.model.RepositoryAlreadyExistsException;

@Component
public class AwsEcrAdapter {

    private static final Logger log = LoggerFactory.getLogger(AwsEcrAdapter.class);

    private final EcrClient ecrClient;

    public AwsEcrAdapter(EcrClient ecrClient) {
        this.ecrClient = ecrClient;
    }

    /**
     * ECR 리포지토리를 생성하고 URI를 반환한다.
     * 이미 존재하면 기존 URI를 그대로 반환한다.
     */
    public String createRepository(String repoName) {
        try {
            var response = ecrClient.createRepository(CreateRepositoryRequest.builder()
                .repositoryName(repoName)
                .imageTagMutability(ImageTagMutability.MUTABLE)
                .build());
            String uri = response.repository().repositoryUri();
            log.info("[ECR] created repository: {}", uri);
            return uri;
        } catch (RepositoryAlreadyExistsException e) {
            var existing = ecrClient.describeRepositories(DescribeRepositoriesRequest.builder()
                .repositoryNames(repoName)
                .build());
            String uri = existing.repositories().getFirst().repositoryUri();
            log.info("[ECR] repository already exists: {}", uri);
            return uri;
        }
    }
}
