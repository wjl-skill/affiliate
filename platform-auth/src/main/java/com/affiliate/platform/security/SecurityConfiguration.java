package com.affiliate.platform.security;

import com.affiliate.platform.security.auth.JwtAuthenticationFilter;
import com.affiliate.platform.security.auth.JwtTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
 * 1. 本地/开发模式 (app.security.enabled=false)：放行所有 HTTP 请求，便于快速联调与单测。
 * 2. 启用模式 (app.security.enabled=true)：使用平台自签发 HS256 JWT 校验
 *    (POST /api/v1/auth/login 签发)，并结合方法级权限管控 (@EnableMethodSecurity)。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    /**
     * 本地开发与单测安全策略：禁用 CSRF 并放行所有路径
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
    SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    /**
     * JWT 鉴权安全策略：除健康检查、登录端点与公开回调链路外，所有请求均需携带有效令牌
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain jwtSecurity(
            HttpSecurity http,
            JwtTokenService jwtTokenService,
            RbacService rbacService
    ) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. 系统与监控探针放行
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        // 2. 身份认证与登录接口放行
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        // 3. 高性能 RTB 竞价与追踪数据面放行 (内置 HMAC 校验与频控保护)
                        .requestMatchers("/rtb/**").permitAll()
                        // 4. 网盟公开点击追踪与 S2S Postback 回传入口放行 (参数防篡改与幂等保护)
                        .requestMatchers("/affiliate/**").permitAll()
                        .requestMatchers("/api/v1/affiliate/skan/postback").permitAll()
                        // 5. 租户管理：创建与状态变更仅限平台超级管理员，列表查询要求管理员
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/tenants/**").hasAnyRole("ADMIN", "SUPER_ADMIN", "TENANT_ADMIN")
                        // 6. 系统底层配置与菜单管理：限制为管理员与平台超级管理员
                        .requestMatchers("/api/v1/system/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // 7. 其余所有 REST 端点均要求已认证身份，并由具体方法级 @PreAuthorize 进行权限细分控制
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"message\":\"未登录或访问令牌已失效\"}");
                }))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenService, rbacService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
