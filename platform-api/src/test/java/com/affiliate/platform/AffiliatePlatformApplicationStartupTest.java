package com.affiliate.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("pg")
class AffiliatePlatformApplicationStartupTest {

    @Test
    void contextLoads() {
        System.out.println(">>> AffiliatePlatformApplication context successfully started with profile 'pg'!");
    }
}
