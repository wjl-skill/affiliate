package com.affiliate.platform.security;

import com.affiliate.platform.security.auth.JwtAuthenticationFilter;
import com.affiliate.platform.security.auth.JwtTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 平台统一 Spring Security 安全过滤链配置 (Platform Security Configuration)
 * <p>
 * 支持双运行模式：
 * 1. 本地/开发模式 (app.security.enabled=false)：放行所有 HTTP 请求，便于快速联调与压测；
 * 2. 启用模式 (app.security.enabled=true)：使用平台自签发 HS256 JWT 校验
 *    (POST /api/v1/auth/login 签发)，无需外部 OAuth2 授权服务器。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    /**
     * 本地开发与单测安全策略：禁用 CSRF 并放行所有路由
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
    SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    /**
     * JWT 鉴权安全策略：除健康检查、登录端点与广告主公开回调链路外，所有请求均需携带有效令牌
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain jwtSecurity(HttpSecurity http, JwtTokenService jwtTokenService) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/tenants").permitAll()
                        // 广告主 S2S 回传与渠道点击流量为公开入口，自带 click_id/幂等保护
                        .requestMatchers("/affiliate/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"message\":\"未登录或访问令牌已失效\"}");
                }))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
