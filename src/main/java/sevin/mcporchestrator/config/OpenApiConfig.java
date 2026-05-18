package sevin.mcporchestrator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("MCP Hub API")
                .description("MCP 서버 레지스트리 및 오케스트레이터 API")
                .version("1.0.0"))
            .servers(List.of(
                new Server().url("/").description("Current server")
            ));
    }
}
