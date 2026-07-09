package com.ligong.reportingcenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// 第一阶段暂不接入 Nacos
// import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// @EnableDiscoveryClient
@EnableScheduling
public class RepairingCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepairingCenterApplication.class, args);
    }
}