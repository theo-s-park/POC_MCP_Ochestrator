package sevin.mcporchestrator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
     * - /api/lambda/** : ROLE_ADMIN 전용 (Lambda 인프라 생성/배포)
     * - 그 외          : 공개 (기존 등록/관리/퍼블릭 API, 백오피스, 웹)
     * - /api/** 미인증 : 302 리다이렉트 대신 401 반환 (fetch/SSE가 로그인 HTML을 받지 않도록)
     * - CSRF 비활성화  : 세션 쿠키 기반 동일 출처 fetch를 위해 (POC)
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/lambda/**").hasRole("ADMIN")
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
