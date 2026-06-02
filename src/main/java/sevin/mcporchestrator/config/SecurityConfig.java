package sevin.mcporchestrator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Backoffice 화면 + 서버 관리 API → ADMIN 전용
     * WebUI + /api/mcp/apps/public → 공개
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Backoffice 화면
                .requestMatchers("/backoffice/**").hasRole("ADMIN")
                // Lambda 인프라 생성/배포
                .requestMatchers("/api/lambda/**").hasRole("ADMIN")
                // 서버 등록·삭제·새로고침
                .requestMatchers(HttpMethod.POST,   "/api/mcp/servers/register", "/api/mcp/servers/register-stream").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/mcp/servers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/mcp/servers/*/refresh").hasRole("ADMIN")
                // 앱 조회 — backoffice(전체) ADMIN, public만 허용
                .requestMatchers(HttpMethod.GET, "/api/mcp/apps/public").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/mcp/apps/**").hasRole("ADMIN")
                // 앱/툴 메타데이터 변경
                .requestMatchers(HttpMethod.PATCH,  "/api/mcp/apps/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,  "/api/mcp/tools/**").hasRole("ADMIN")
                // 나머지 (WebUI, 로그인, OSS API 등) 공개
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/backoffice?tab=create", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/backoffice")
                .permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                PathPatternRequestMatcher.withDefaults().matcher("/api/**")
            ));
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
        @Value("${admin.username:admin}") String username,
        @Value("${admin.password:}") String rawPassword,
        PasswordEncoder encoder) {

        String pw = (rawPassword == null || rawPassword.isBlank()) ? "admin1234" : rawPassword;
        if (rawPassword == null || rawPassword.isBlank()) {
            log.warn("[Security] ADMIN_PASSWORD 미설정 — 기본값 'admin1234' 사용. 운영 환경에서는 반드시 env로 지정하세요.");
        }

        UserDetails admin = User.withUsername(username)
            .password(encoder.encode(pw))
            .roles("ADMIN")
            .build();
        log.info("[Security] admin account ready: username={}", username);
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
