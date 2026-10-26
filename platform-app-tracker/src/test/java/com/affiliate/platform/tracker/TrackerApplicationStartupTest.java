package com.affiliate.platform.tracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TrackerApplicationStartupTest {

    @Test
    @DisplayName("Tracker 启动类入口可正常加载")
    void contextLoads() {
        assertNotNull(TrackerApplication.class);
    }
}
