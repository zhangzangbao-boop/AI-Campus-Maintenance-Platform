package com.ligong.reportingcenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.qiyun.feign.client")
@EnableScheduling
@ComponentScan(basePackages = {
    "com.ligong.reportingcenter",
    "com.qiyun.common"
})
public class RepairingCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepairingCenterApplication.class, args);
    }
}