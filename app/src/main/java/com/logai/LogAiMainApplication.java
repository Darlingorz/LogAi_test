package com.logai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.logai")
@MapperScan({
        "com.logai.user.mapper",
        "com.logai.security.mapper",
        "com.logai.context.mapper",
        "com.logai.creem.mapper"
})
public class LogAiMainApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogAiMainApplication.class, args);
    }
}
