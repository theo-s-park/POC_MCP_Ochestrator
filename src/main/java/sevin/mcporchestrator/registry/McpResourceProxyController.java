package sevin.mcporchestrator.registry;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import sevin.mcporchestrator.common.exception.ErrorCode;
import sevin.mcporchestrator.common.exception.McpResourceException;
import sevin.mcporchestrator.common.exception.McpServerNotFoundException;
import sevin.mcporchestrator.registry.domain.McpServerRecord;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Tag(name = "MCP Resources", description = "MCP 서버 리소스 프록시 (resources/read)")
@RestController
public class McpResourceProxyController {

    private static final Logger log = LoggerFactory.getLogger(McpResourceProxyController.class);

    private final McpServerRegistry registry;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public McpResourceProxyController(McpServerRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();
    }

    @GetMapping("/api/mcp/servers/{serverId}/resources/content")
    public ResponseEntity<byte[]> readResource(
            @PathVariable String serverId,
            @RequestParam String uri) {

        McpServerRecord server = registry.find(serverId)
            .orElseThrow(McpServerNotFoundException::new);

        try {
            Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "resources/read",
                "params", Map.of("uri", uri)
            );

            String responseBody = restClient.post()
                .uri(server.getUrl() + "/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(request))
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contents = root.path("result").path("contents");

            if (!contents.isArray() || contents.isEmpty()) {
                throw new McpResourceException(ErrorCode.RESOURCE_NOT_FOUND);
            }

            JsonNode content = contents.get(0);
            String mimeType = content.path("mimeType").asText("application/octet-stream");

            if (content.has("blob")) {
                byte[] bytes = Base64.getDecoder().decode(content.path("blob").asText());
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(bytes);
            }

            if (content.has("text")) {
                byte[] bytes = content.path("text").asText().getBytes(StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(bytes);
            }

            throw new McpResourceException(ErrorCode.RESOURCE_NOT_FOUND);

        } catch (McpResourceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ResourceProxy] resources/read failed - server {}, uri {}: {}", serverId, uri, e.getMessage());
            throw new McpResourceException(ErrorCode.RESOURCE_UPSTREAM_ERROR);
        }
    }
}
