package com.affiliate.platform.rtb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = RtbApplication.class)
@ActiveProfiles("pg")
class RtbApplicationStartupTest {

    @Test
    @DisplayName("RTB 启动类入口与 Spring 容器可正常加载")
    void contextLoads() {
        assertNotNull(RtbApplication.class);
    }
}
