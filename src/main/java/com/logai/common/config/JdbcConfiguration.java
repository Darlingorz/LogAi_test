package com.logai.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class JdbcConfiguration {

    @Value("${spring.datasource.cloud-sql-connection-name}")
    private String CLOUD_SQL_CONNECTION_NAME;

    @Value("${spring.datasource.database-name}")
    private String DB_NAME;

    @Value("${spring.datasource.iam-db-user}")
    private String IAM_DB_USER;

    @Bean
    public DataSource dataSource() {
        Properties props = new Properties();
        props.setProperty("user", IAM_DB_USER);
        props.setProperty("socketFactory", "com.google.cloud.sql.mysql.SocketFactory");
        props.setProperty("cloudSqlInstance", CLOUD_SQL_CONNECTION_NAME);
        props.setProperty("enableIamAuth", "true");
        props.setProperty("sslmode", "disable");

        String jdbcUrl = String.format(
                "jdbc:mysql://google/%s?socketFactory=com.google.cloud.sql.mysql.SocketFactory&cloudSqlInstance=%s",
                DB_NAME, CLOUD_SQL_CONNECTION_NAME
        );

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDataSourceProperties(props);

        // ---- Hikari Cloud SQL Optimized ----
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(10);       // 免费版不要超过 10
        config.setIdleTimeout(30_000);       // Cloud SQL Idle Disconnect
        config.setMaxLifetime(300_000);      // 防僵尸
        config.setConnectionTimeout(10_000); // 10s
        config.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(config);
    }

}