package com.logai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.logai.**.mapper")
public class LogAiMainApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogAiMainApplication.class, args);
    }
}
