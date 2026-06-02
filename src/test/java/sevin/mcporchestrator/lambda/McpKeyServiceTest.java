package sevin.mcporchestrator.lambda;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import sevin.mcporchestrator.lambda.application.McpKeyService;

import static org.assertj.core.api.Assertions.assertThat;

class McpKeyServiceTest {

    private McpKeyService service;

    @BeforeEach
    void setUp() {
        service = new McpKeyService("test-secret-key-32bytes-padding!!");
    }

    @Test
    void generateKey_formatIsCorrect() {
        String key = service.generateKey();
        assertThat(key).startsWith("mcp_");
        assertThat(key).hasSize(68); // "mcp_" + 64 hex chars
        assertThat(key.substring(4)).matches("[0-9a-f]{64}");
    }

    @RepeatedTest(5)
    void generateKey_isUnique() {
        assertThat(service.generateKey()).isNotEqualTo(service.generateKey());
    }

    @Test
    void encryptDecrypt_roundtrip() {
        String key = service.generateKey();
        String encrypted = service.encrypt(key);
        String decrypted = service.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(key);
    }

    @Test
    void encrypt_differentIvEachTime() {
        String key = service.generateKey();
        String enc1 = service.encrypt(key);
        String enc2 = service.encrypt(key);
        assertThat(enc1).isNotEqualTo(enc2); // 같은 plaintext도 매번 다른 암호문
        assertThat(service.decrypt(enc1)).isEqualTo(key);
        assertThat(service.decrypt(enc2)).isEqualTo(key);
    }
}
