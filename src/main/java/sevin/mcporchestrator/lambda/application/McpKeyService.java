package sevin.mcporchestrator.lambda.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * MCP 서버 API 키 생성·암호화·복호화 서비스.
 *
 * 키 포맷: "mcp_" + 32바이트 랜덤값의 hex (총 68자)
 * 저장 포맷: Base64(12바이트 IV + AES-256-GCM 암호문)
 */
@Service
public class McpKeyService {

    private static final Logger log = LoggerFactory.getLogger(McpKeyService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public McpKeyService(@Value("${mcp.orchestrator.secret-key:mcp-default-secret-key-change-me!!}") String rawKey) {
        byte[] keyBytes = rawKey.getBytes();
        byte[] key32 = new byte[32];
        System.arraycopy(keyBytes, 0, key32, 0, Math.min(keyBytes.length, 32));
        this.secretKey = new SecretKeySpec(key32, "AES");

        if (rawKey.equals("mcp-default-secret-key-change-me!!")) {
            log.warn("[McpKey] MCP_ORCHESTRATOR_SECRET_KEY 미설정 — 기본값 사용. 운영 환경에서는 반드시 env로 지정하세요.");
        }
    }

    /** mcp_ + 32바이트 랜덤 hex 키 생성 */
    public String generateKey() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return "mcp_" + HexFormat.of().formatHex(bytes);
    }

    /** 키를 AES-256-GCM으로 암호화해 Base64 반환 */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            byte[] combined = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_LENGTH, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("MCP key encryption failed", e);
        }
    }

    /** Base64 암호문을 복호화해 plaintext 반환 */
    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new RuntimeException("MCP key decryption failed", e);
        }
    }
}
