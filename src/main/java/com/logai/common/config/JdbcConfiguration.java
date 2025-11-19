package com.logai.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

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
        // 1. 初始化连接池配置
        HikariConfig config = new HikariConfig();
        // 2. 设置基础 JDBC URL
        // 格式: jdbc:mysql:///<DATABASE_NAME>
        config.setJdbcUrl(String.format("jdbc:mysql:///%s", DB_NAME));

        // 3. 设置 IAM 用户名
        config.setUsername(IAM_DB_USER);

        // 4. 设置密码 (必须设置一个非空字符串，虽然 IAM 认证不校验它，但驱动层会检查)
        config.setPassword("password");

        // 5. 设置 Cloud SQL 特定的 SocketFactory 属性
        // 对应文档中的 IAM Authentication Example
        config.addDataSourceProperty("socketFactory", "com.google.cloud.sql.mysql.SocketFactory");
        config.addDataSourceProperty("cloudSqlInstance", CLOUD_SQL_CONNECTION_NAME);
        config.addDataSourceProperty("enableIamAuth", "true");

        // 可选：如果想强制使用内网 IP，可以取消注释下面这行
        // config.addDataSourceProperty("ipTypes", "PRIVATE");

        // 6. 其他连接池通用配置 (超时时间等)
        config.setConnectionTimeout(10000); // 10秒
        config.setMaximumPoolSize(10);      // 最大连接数

        // 7. 必须指定 Driver Class，否则可能报错
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        return new HikariDataSource(config);
    }

}