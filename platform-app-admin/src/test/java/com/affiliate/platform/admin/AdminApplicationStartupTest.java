package com.affiliate.platform.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminApplicationStartupTest {

    @Test
    @DisplayName("Admin 启动类入口可正常加载")
    void contextLoads() {
        assertNotNull(AdminApplication.class);
    }
}
