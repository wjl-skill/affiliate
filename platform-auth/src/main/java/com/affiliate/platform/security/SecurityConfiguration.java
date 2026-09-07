package com.affiliate.platform.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 平台统一 Spring Security 安全过滤链配置 (Platform Security Configuration)
 * <p>
 * 支持双运行模式：
 * 1. 本地/开发模式 (app.security.enabled=false)：放行所有 HTTP 请求，便于快速联调与压测；
 * 2. 生产模式 (app.security.enabled=true)：启用 OAuth2 Resource Server，强制校验 JWT Token 并提取多租户 Claim。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    /**
     * 本地开发与单测安全策略：禁用 CSRF 并放行所有路由
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    /**
     * 生产环境安全策略：除健康检查外，所有 API 请求均要求 OAuth2 JWT 认证
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
    SecurityFilterChain productionSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll() // 放行 K8s 存活探针
                        .anyRequest().authenticated() // 其余接口均需携带有效 JWT
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new TenantJwtAuthenticationConverter()))
                )
                .build();
    }
}
