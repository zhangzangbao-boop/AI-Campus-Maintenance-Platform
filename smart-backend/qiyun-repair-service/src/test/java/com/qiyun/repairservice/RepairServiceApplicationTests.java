package com.qiyun.repairservice;

import com.qiyun.feign.client.AiServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RepairServiceApplicationTests {

    @MockBean
    private AiServiceClient aiServiceClient;

    @Test
    void contextLoads() {
    }
}