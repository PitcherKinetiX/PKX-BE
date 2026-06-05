package com.pkx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@EnableFeignClients
public class PkxApplication {

    public static void main(String[] args) {
        SpringApplication.run(PkxApplication.class, args);
    }

}
