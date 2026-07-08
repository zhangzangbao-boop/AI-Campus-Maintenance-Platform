package com.ligong.reportingcenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RepairingCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepairingCenterApplication.class, args);
    }
}