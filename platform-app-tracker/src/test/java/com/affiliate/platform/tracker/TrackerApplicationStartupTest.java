package com.affiliate.platform.tracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = TrackerApplication.class)
@ActiveProfiles("pg")
class TrackerApplicationStartupTest {

    @Test
    @DisplayName("Tracker 启动类入口与 Spring 容器可正常加载")
    void contextLoads() {
        assertNotNull(TrackerApplication.class);
    }
}
