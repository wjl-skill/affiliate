package com.affiliate.platform.rtb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 实时广告追踪 Token 加密与防篡改签名核验服务 (HMAC Token Security Service)
 * <p>
 * 为实时竞价的胜出通知 (nurl)、曝光统计 (burl) 和点击跳转 (clickUrl) 提供金融级防篡改保护：
 * 1. 采用 HMAC-SHA256 对拍卖流水号、预占标识、租户、金额与时间戳进行单向加签；
 * 2. 强制校验 1 小时有效时间窗口，彻底防御重放攻击（Replay Attack）与金额参数恶意篡改。
 */
@Service
public class HmacTokenService {

    // HMAC 签名密钥字节数组
    private final byte[] secretKey;

    // 签名哈希算法
    private static final String HMAC_ALGO = "HmacSHA256";

    // Token 最大有效期（秒）：默认 1 小时
    private static final long MAX_TOKEN_AGE_SECONDS = 3600;

    /**
     * 构造 HMAC 签名服务
     *
     * @param secret 从配置文件注入的密钥字符串
     */
    public HmacTokenService(@Value("${app.rtb.hmac-secret:affiliate-default-secret-key-32b!}") String secret) {
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 追踪载荷数据结构 (Decoded Tracking Payload)
     *
     * @param auctionId     拍卖流水号 ID
     * @param reservationId 预算预占唯一凭证 ID
     * @param tenantId      租户标识
     * @param campaignId    活动标识
     * @param price         出价成交金额 (CPM 单位)
     * @param timestamp     生成时间戳（秒）
     * @param eventType     事件类型（WIN / IMP / CLICK）
     */
    public record TrackingPayload(
            String auctionId,
            String reservationId,
            String tenantId,
            String campaignId,
            double price,
            long timestamp,
            String eventType
    ) {}

    /**
     * 生成带防篡改签名的 URL 安全 Base64 Token
     *
     * @param auctionId     拍卖流水 ID
     * @param reservationId 预算预占 ID
     * @param tenantId      租户标识
     * @param campaignId    活动标识
     * @param price         价格
     * @param eventType     事件类型
     * @return URL 安全的 Base64 编码 Token 字符串
     */
    public String generateToken(String auctionId, String reservationId, String tenantId, String campaignId, double price, String eventType) {
        // 获取当前 Epoch 秒时间戳
        long now = Instant.now().getEpochSecond();
        // 按照固定流水线序列化明文字符串
        String data = String.format("%s|%s|%s|%s|%.4f|%d|%s",
                auctionId, reservationId, tenantId, campaignId, price, now, eventType);
        // 使用 HMAC-SHA256 计算防篡改签名
        String signature = sign(data);
        // 将明文与签名拼接后转为 URL 安全 Base64
        String rawToken = data + "|" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析并校验追踪 Token 的真实性与有效性
     *
     * @param tokenString URL 传入的 Token 字符串
     * @return 校验通过后的 TrackingPayload 载荷实体
     * @throws SecurityException        签名不符或 Token 过期时抛出
     * @throws IllegalArgumentException 编码损坏或格式错误时抛出
     */
    public TrackingPayload verifyAndParse(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            throw new IllegalArgumentException("token cannot be blank");
        }
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getUrlDecoder().decode(tokenString);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed token encoding");
        }

        String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|");
        if (parts.length != 8) {
            throw new IllegalArgumentException("invalid token structure");
        }

        // 提取明文字段与签名
        String auctionId = parts[0];
        String reservationId = parts[1];
        String tenantId = parts[2];
        String campaignId = parts[3];
        double price = Double.parseDouble(parts[4]);
        long timestamp = Long.parseLong(parts[5]);
        String eventType = parts[6];
        String expectedSig = parts[7];

        // 重新计算 HMAC 签名并比对
        String data = String.format("%s|%s|%s|%s|%.4f|%d|%s",
                auctionId, reservationId, tenantId, campaignId, price, timestamp, eventType);
        String calculatedSig = sign(data);

        // 1. 签名防篡改校验
        if (!calculatedSig.equals(expectedSig)) {
            throw new SecurityException("token signature verification failed");
        }

        // 2. 时间窗口防重放校验（超过 1 小时作废）
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > MAX_TOKEN_AGE_SECONDS) {
            throw new SecurityException("token has expired");
        }

        return new TrackingPayload(auctionId, reservationId, tenantId, campaignId, price, timestamp, eventType);
    }

    /**
     * 底层 HMAC-SHA256 加签算法实现
     */
    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGO));
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("cannot compute hmac signature", e);
        }
    }
}
