package com.codebuzzers.codebuzzcore;

import com.codebuzzers.codebuzzcore.common.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class CodeBuzzcoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeBuzzcoreApplication.class, args);
    }
}
