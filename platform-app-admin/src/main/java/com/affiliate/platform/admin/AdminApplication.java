package com.affiliate.platform.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 运营控制面与管理中台独立启动单元 (Admin & Control Plane Application Entry)
 * <p>
 * 企业级管理服务：
 * 负责 RBAC 权限与用户认证、租户生命周期管理、DSP 活动投放管理、
 * 报表分析、不可变双分录账本资金管理、出海打款审核与 Google Ads/GAM 渠道同步。
 */
@SpringBootApplication(scanBasePackages = {"com.affiliate.platform"})
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
