package com.affiliate.platform.integration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

/**
 * 统一跨平台 S2S / CAPI 广告转化回传数据载荷 (Unified Server-to-Server Conversion Payload)
 * <p>
 * 适配 Google Ads Enhanced Conversions、Meta Conversions API (CAPI) 及 TikTok Events API。
 * 内置对 email 和 phone 的 SHA-256 哈希辅助处理，保障合规与用户隐私安全。
 *
 * @param platform         目标平台代码（如 GOOGLE_ADS, TIKTOK, FACEBOOK）
 * @param accountOrPixelId 广告账户 ID / 转化操作 ID / Pixel ID
 * @param accessToken      API 授权令牌
 * @param eventName        标准化转化事件名称（如 PURCHASE, LEAD, COMPLETE_REGISTRATION）
 * @param orderId          唯一订单/交易号（用于服务端去重）
 * @param value            转化金额
 * @param currency         结算币种（如 USD, CNY）
 * @param eventTime        转化发生时间戳
 * @param gclid            Google Click ID
 * @param fbclid           Facebook Click ID
 * @param fbp              Facebook 浏览器 Cookie ID (_fbp)
 * @param fbc              Facebook 点击参数 Cookie ID (_fbc)
 * @param ttclid           TikTok Click ID
 * @param rawEmail         原始用户邮箱（未哈希）
 * @param rawPhone         原始用户手机号（未哈希）
 * @param clientIp         访客 IP 地址
 * @param userAgent        访客 User-Agent 字符串
 * @param customParams     自定义扩展参数键值对
 */
public record UnifiedConversionPayload(
        String platform,
        String accountOrPixelId,
        String accessToken,
        String eventName,
        String orderId,
        BigDecimal value,
        String currency,
        Instant eventTime,
        String gclid,
        String fbclid,
        String fbp,
        String fbc,
        String ttclid,
        String rawEmail,
        String rawPhone,
        String clientIp,
        String userAgent,
        Map<String, Object> customParams
) {
    public UnifiedConversionPayload {
        currency = (currency != null && !currency.isBlank()) ? currency : "USD";
        eventTime = eventTime == null ? Instant.now() : eventTime;
        customParams = customParams == null ? Map.of() : Map.copyOf(customParams);
    }

    /**
     * 计算小写清理后的邮箱 SHA-256 哈希值
     */
    public String getHashedEmail() {
        if (rawEmail == null || rawEmail.isBlank()) return null;
        return sha256(rawEmail.trim().toLowerCase());
    }

    /**
     * 计算去除特殊字符后的手机号 SHA-256 哈希值 (遵循 E.164 规范建议)
     */
    public String getHashedPhone() {
        if (rawPhone == null || rawPhone.isBlank()) return null;
        String cleanPhone = rawPhone.trim().replaceAll("[\\s\\-\\(\\)]", "");
        return sha256(cleanPhone);
    }

    /**
     * SHA-256 哈希通用计算工具方法
     */
    public static String sha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate SHA-256 hash", e);
        }
    }
}
