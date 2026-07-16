package com.qiyun.repairservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 报修服务主启动类
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.qiyun.feign")
@EnableScheduling
public class RepairServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepairServiceApplication.class, args);
    }
}