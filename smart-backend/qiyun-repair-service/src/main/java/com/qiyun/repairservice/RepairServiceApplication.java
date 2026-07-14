package com.qiyun.repairservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 报修服务主启动类
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.qiyun.feign")
public class RepairServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepairServiceApplication.class, args);
    }
}