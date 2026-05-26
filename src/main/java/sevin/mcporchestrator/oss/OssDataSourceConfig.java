package sevin.mcporchestrator.oss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class OssDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(OssDataSourceConfig.class);

    @Bean("ossJdbcTemplate")
    @ConditionalOnExpression("!'${oss.datasource.url:}'.isEmpty()")
    public JdbcTemplate ossJdbcTemplate(
            @Value("${oss.datasource.url}") String url,
            @Value("${oss.datasource.username:}") String username,
            @Value("${oss.datasource.password:}") String password) {
        log.info("[OSS] Connecting to OSS DB: {}", url);
        DataSource ds = DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .build();
        return new JdbcTemplate(ds);
    }
}
