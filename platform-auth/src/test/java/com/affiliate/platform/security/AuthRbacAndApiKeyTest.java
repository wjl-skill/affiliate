package com.affiliate.platform.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AuthRbacAndApiKeyTest {

    @Test
    void rbacRolePermissionCheck() {
        RbacService rbac = new RbacService();

        // 超级管理员拥有一切权限
        assertTrue(rbac.hasPermission(RbacService.Role.SUPER_ADMIN, "anything:special"));

        // 投放运营人员拥有活动写与素材写权限，但无审核与财务权限
        assertTrue(rbac.hasPermission(RbacService.Role.TRAFFICKER, "campaign:write"));
        assertTrue(rbac.hasPermission(RbacService.Role.TRAFFICKER, "creative:write"));
        assertFalse(rbac.hasPermission(RbacService.Role.TRAFFICKER, "creative:approve"));
        assertFalse(rbac.hasPermission(RbacService.Role.TRAFFICKER, "billing:write"));

        // 审核人员拥有素材审核权限
        assertTrue(rbac.hasPermission(RbacService.Role.AUDITOR, "creative:approve"));
        assertFalse(rbac.hasPermission(RbacService.Role.AUDITOR, "campaign:write"));

        // 财务人员拥有账单写权限
        assertTrue(rbac.hasPermission(RbacService.Role.FINANCE, "billing:write"));
        assertFalse(rbac.hasPermission(RbacService.Role.FINANCE, "creative:write"));
    }

    @Test
    void apiKeyHmacSignatureVerification() {
        ApiKeyService service = new ApiKeyService();

        String keyId = "key_partner_dsp_01";
        String secret = "secret_key_8888_secure";

        service.registerKey(new ApiKeyService.ApiKeyInfo(keyId, secret, "tenant_1", true));

        long now = Instant.now().getEpochSecond();
        String payload = "{\"bidder\":\"DSP_X\",\"qps\":500}";

        // 客户端生成正确签名
        String validSig = service.sign(secret, now, payload);
        assertTrue(service.verify(keyId, now, payload, validSig));

        // 篡改请求体内容 -> 验签失败
        assertFalse(service.verify(keyId, now, payload + "_tampered", validSig));

        // 防重放攻击测试：10分钟前 (600秒) 的过期请求 -> 验签失败
        long expiredTime = now - 600;
        String expiredSig = service.sign(secret, expiredTime, payload);
        assertFalse(service.verify(keyId, expiredTime, payload, expiredSig));
    }
}
