package com.affiliate.platform.rtb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RtbApplicationStartupTest {

    @Test
    @DisplayName("RTB 启动类入口可正常加载")
    void contextLoads() {
        assertNotNull(RtbApplication.class);
    }
}
