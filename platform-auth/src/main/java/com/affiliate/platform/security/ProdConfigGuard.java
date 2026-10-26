package com.affiliate.platform.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 生产环境严格配置与凭据安全守护器 (Production Configuration Guard)
 * <p>
 * 遵循阶段 0 安全底线原则：
 * 当应用激活 "prod" Profile 时，强制执行关键凭证强度、安全开关与合规性静态断言。
 * 若检测到明文弱口令、缺失必须环境变量或危险的反序列化配置，立即阻断启动，杜绝带病上线。
 */
@Component
public class ProdConfigGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProdConfigGuard.class);

    private static final List<String> FORBIDDEN_SECRET_PATTERNS = List.of(
            "change-me", "changeme", "dev-jwt-secret", "please-rotate",
            "123456", "password", "admin", "secret", "default", "root"
    );

    private final Environment environment;

    public ProdConfigGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProd = Arrays.stream(activeProfiles).anyMatch(p -> p.equalsIgnoreCase("prod"));

        if (!isProd) {
            log.info("[ProdConfigGuard] 非生产 Profile 启动 (profiles: {})，跳过生产级凭证强校验", Arrays.toString(activeProfiles));
            return;
        }

        log.info("[ProdConfigGuard] 检测到生产环境 (Profile: prod)，开始执行系统安全基线与凭据合规断言...");

        // 1. 验证安全模块开关必须显式启用
        String securityEnabled = environment.getProperty("app.security.enabled", "true");
        if (!"true".equalsIgnoreCase(securityEnabled)) {
            throw new IllegalStateException("[CRITICAL SECURITY] 生产环境必须强制启用安全鉴权过滤 (app.security.enabled 必须为 true)");
        }

        // 2. 验证 JWT Secret
        String jwtSecret = environment.getProperty("app.security.jwt-secret");
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("[CRITICAL SECURITY] 生产环境未注入有效的 app.security.jwt-secret (APP_JWT_SECRET 环境变量)");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("[CRITICAL SECURITY] 生产环境 JWT 密钥强度不足，HS256 签名要求有效密钥字节长度 >= 32 字节 (当前长度: "
                    + jwtSecret.getBytes(StandardCharsets.UTF_8).length + ")");
        }
        String lowerJwt = jwtSecret.toLowerCase();
        for (String forbidden : FORBIDDEN_SECRET_PATTERNS) {
            if (lowerJwt.contains(forbidden)) {
                throw new IllegalStateException("[CRITICAL SECURITY] 生产环境 JWT 密钥包含已知的测试默认弱口令特征: '" + forbidden + "'，请立即从外部 Secrets Manager (Vault/KMS) 注入强随机凭据");
            }
        }

        // 3. 验证数据库密码
        String dbPassword = environment.getProperty("spring.datasource.password");
        if (dbPassword != null && !dbPassword.isBlank()) {
            String lowerDbPwd = dbPassword.toLowerCase();
            for (String forbidden : FORBIDDEN_SECRET_PATTERNS) {
                if (lowerDbPwd.equals(forbidden)) {
                    throw new IllegalStateException("[CRITICAL SECURITY] 生产数据库连接密码使用了内置默认弱口令: '" + dbPassword + "'，已被安全守护器拦截");
                }
            }
        }

        // 4. 验证 Redis 密码
        String redisPassword = environment.getProperty("spring.data.redis.password");
        if (redisPassword != null && !redisPassword.isBlank()) {
            String lowerRedisPwd = redisPassword.toLowerCase();
            for (String forbidden : FORBIDDEN_SECRET_PATTERNS) {
                if (lowerRedisPwd.equals(forbidden)) {
                    throw new IllegalStateException("[CRITICAL SECURITY] 生产 Redis 认证密码使用了内置默认弱口令: '" + redisPassword + "'，已被安全守护器拦截");
                }
            }
        }

        // 5. 验证 Kafka 反序列化白名单杜绝通配符 "*"
        String kafkaTrusted = environment.getProperty("spring.kafka.consumer.properties.spring.json.trusted.packages");
        if ("*".equals(kafkaTrusted)) {
            throw new IllegalStateException("[CRITICAL SECURITY] 生产 Kafka 消费者配置 spring.json.trusted.packages 不能为 '*'，存在远程代码执行安全隐患");
        }

        // 6. 验证广告主 S2S 转化回传在生产环境强制鉴权防篡改
        String postbackAuth = environment.getProperty("app.security.postback-auth-required", "true");
        if (!"true".equalsIgnoreCase(postbackAuth)) {
            throw new IllegalStateException("[CRITICAL SECURITY] 生产环境必须强制开启广告主 S2S 回传鉴权 (app.security.postback-auth-required 必须为 true)，杜绝外部伪造转化");
        }

        log.info("[ProdConfigGuard] 生产安全基线验证 100% 通过：JWT密钥、数据源鉴权、中间件通信、反序列化边界与S2S签名防护均符合商业级合规标准。");
    }
}
