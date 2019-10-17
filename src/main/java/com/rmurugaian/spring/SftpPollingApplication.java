package com.rmurugaian.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.integration.config.EnableIntegration;

@SpringBootApplication
@EnableIntegration
public class SftpPollingApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SftpPollingApplication.class, args);
    }

}

