package com.affiliate.platform.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdConfigGuardTest {

    @Test
    @DisplayName("非 prod 环境应静默放行，不影响本地联调")
    void testNonProdPasses() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "local");
        ProdConfigGuard guard = new ProdConfigGuard(env);

        assertThatCode(() -> guard.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod 环境下若安全模块被显式禁用，必须抛出异常阻断")
    void testProdSecurityDisabledThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("app.security.enabled", "false");
        ProdConfigGuard guard = new ProdConfigGuard(env);

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境必须强制启用安全鉴权过滤");
    }

    @Test
    @DisplayName("prod 环境下若 JWT 密钥缺失或过短，必须抛出异常阻断")
    void testProdJwtMissingOrShortThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("app.security.enabled", "true");
        ProdConfigGuard guard = new ProdConfigGuard(env);

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未注入有效的 app.security.jwt-secret");

        env.setProperty("app.security.jwt-secret", "short-secret");
        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HS256 签名要求有效密钥字节长度 >= 32 字节");
    }

    @Test
    @DisplayName("prod 环境下若 JWT 密钥包含测试默认弱口令，必须抛出异常阻断")
    void testProdJwtWeakPatternThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("app.security.enabled", "true");
        env.setProperty("app.security.jwt-secret", "affiliate-platform-dev-jwt-secret-change-me-in-prod-0518-long-enough");
        ProdConfigGuard guard = new ProdConfigGuard(env);

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("包含已知的测试默认弱口令特征");
    }

    @Test
    @DisplayName("prod 环境下若 Kafka trusted.packages 为通配符 *，必须抛出异常阻断")
    void testProdKafkaWildcardThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("app.security.enabled", "true");
        env.setProperty("app.security.jwt-secret", "a".repeat(40));
        env.setProperty("spring.kafka.consumer.properties.spring.json.trusted.packages", "*");
        ProdConfigGuard guard = new ProdConfigGuard(env);

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.json.trusted.packages 不能为 '*'");
    }

    @Test
    @DisplayName("prod 环境下若关闭 postback-auth-required，必须抛出异常阻断")
    void testProdPostbackAuthDisabledThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("app.security.enabled", "true");
        env.setProperty("app.security.jwt-secret", "c2VjdXJlLXByb2R1Y3Rpb24tZmluYW5jaWFsLWdyYWRlLTItc2VjcmV0");
        env.setProperty("spring.datasource.password", "k9#Vp!8zL@2wQx7$ProductionDb");
        env.setProperty("spring.data.redis.password", "m3$Ty!1xR@9vBn4#ProductionRedis");
        env.setProperty("spring.kafka.consumer.properties.spring.json.trusted.packages", "com.affiliate.platform.*");
        env.setProperty("app.security.postback-auth-required", "false");
        ProdConfigGuard guard = new ProdConfigGuard(env);

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境必须强制开启广告主 S2S 回传鉴权");
    }

    @Test
    @DisplayName("prod 环境下全量凭证合规且高强度，必须顺利通过断言")
    void testProdValidConfigPasses() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("app.security.enabled", "true");
        env.setProperty("app.security.jwt-secret", "c2VjdXJlLXByb2R1Y3Rpb24tZmluYW5jaWFsLWdyYWRlLTItc2VjcmV0");
        env.setProperty("spring.datasource.password", "k9#Vp!8zL@2wQx7$ProductionDb");
        env.setProperty("spring.data.redis.password", "m3$Ty!1xR@9vBn4#ProductionRedis");
        env.setProperty("spring.kafka.consumer.properties.spring.json.trusted.packages", "com.affiliate.platform.*");
        ProdConfigGuard guard = new ProdConfigGuard(env);

        assertThatCode(() -> guard.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }
}
