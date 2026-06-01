package sevin.mcporchestrator.lambda.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.CreateRepositoryRequest;
import software.amazon.awssdk.services.ecr.model.DescribeRepositoriesRequest;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.ecr.model.ImageTagMutability;
import software.amazon.awssdk.services.ecr.model.RepositoryAlreadyExistsException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Base64;

@Component
public class AwsEcrAdapter {

    private static final Logger log = LoggerFactory.getLogger(AwsEcrAdapter.class);

    private final EcrClient ecrClient;

    public AwsEcrAdapter(EcrClient ecrClient) {
        this.ecrClient = ecrClient;
    }

    /**
     * ECR 리포지토리를 생성하고 URI를 반환한다. 이미 존재하면 기존 URI를 반환한다.
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

    /**
     * public.ecr.aws 베이스 이미지를 private ECR에 placeholder로 push한다.
     * 이 이미지를 기반으로 Lambda shell을 즉시 생성할 수 있다.
     * 개발자가 실제 이미지를 push하면 update-function-code로 교체된다.
     */
    public void pushPlaceholderImage(String ecrRepoUri, String publicBaseImage) {
        String registry = ecrRepoUri.split("/")[0];
        String password = getEcrLoginPassword();
        String imageWithTag = ecrRepoUri + ":latest";

        log.info("[ECR] pushing placeholder image {} → {}", publicBaseImage, imageWithTag);

        exec(new String[]{"docker", "login", "--username", "AWS", "--password-stdin", registry},
            password.getBytes());
        exec(new String[]{"docker", "pull", publicBaseImage}, null);
        exec(new String[]{"docker", "tag", publicBaseImage, imageWithTag}, null);
        exec(new String[]{"docker", "push", imageWithTag}, null);

        log.info("[ECR] placeholder pushed: {}", imageWithTag);
    }

    private String getEcrLoginPassword() {
        var token = ecrClient.getAuthorizationToken(GetAuthorizationTokenRequest.builder().build());
        String authData = token.authorizationData().getFirst().authorizationToken();
        String decoded = new String(Base64.getDecoder().decode(authData));
        return decoded.split(":", 2)[1];
    }

    private void exec(String[] cmd, byte[] stdin) {
        try {
            Process proc = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

            if (stdin != null) {
                try (OutputStream os = proc.getOutputStream()) {
                    os.write(stdin);
                }
            }

            String output = new BufferedReader(new InputStreamReader(proc.getInputStream()))
                .lines()
                .reduce("", (a, b) -> a + "\n" + b);

            int exit = proc.waitFor();
            log.debug("[ECR] {} exit={} output={}", cmd[1], exit, output.substring(0, Math.min(200, output.length())));

            if (exit != 0) {
                throw new RuntimeException("Docker command failed [" + cmd[1] + "]: " + output.trim());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Docker command error [" + cmd[1] + "]: " + e.getMessage(), e);
        }
    }
}
