package com.affiliate.platform.affiliate.cache;

import org.springframework.stereotype.Component;

/**
 * 缓存键生成器
 * <p>
 * 统一管理所有缓存键的命名规范，防止键冲突
 * <p>
 * 命名规则：{namespace}:{entity}:{id}:{suffix}
 * 例如：affiliate:api_key:key_abc123:validation
 */
@Component
public class CacheKeyGenerator {

    private static final String SEPARATOR = ":";
    private static final String NAMESPACE = "affiliate";

    // ========== API Key 相关 ==========

    public String apiKeyValidation(String keyId) {
        return buildKey("api_key", keyId, "validation");
    }

    public String apiKeyById(String keyId) {
        return buildKey("api_key", keyId);
    }

    public String apiKeysByAffiliate(String affiliateId) {
        return buildKey("api_key", "affiliate", affiliateId);
    }

    public String apiKeyUsageStats(String keyId) {
        return buildKey("api_key", keyId, "usage_stats");
    }

    // ========== Payment 相关 ==========

    public String paymentMethod(String paymentMethodId) {
        return buildKey("payment", "method", paymentMethodId);
    }

    public String paymentMethodsByAffiliate(String affiliateId) {
        return buildKey("payment", "methods", affiliateId);
    }

    public String affiliatePaymentMethods(String affiliateId) {
        return buildKey("payment", "methods", affiliateId);
    }

    public String paymentTransaction(String transactionId) {
        return buildKey("payment", "transaction", transactionId);
    }

    public String paymentBatch(String batchId) {
        return buildKey("payment", "batch", batchId);
    }

    // ========== Product Feed 相关 ==========

    public String product(String sku) {
        return buildKey("product", sku);
    }

    public String productsByOffer(String offerId) {
        return buildKey("product", "offer", offerId);
    }

    public String productFeed(String feedId) {
        return buildKey("product", "feed", feedId);
    }

    public String productCategory(String categoryId) {
        return buildKey("product", "category", categoryId);
    }

    // ========== Geolocation 相关 ==========

    public String geolocation(String ipAddress) {
        return buildKey("geo", "ip", sanitizeIp(ipAddress));
    }

    public String ipGeolocation(String ipAddress) {
        return buildKey("geo", "ip", sanitizeIp(ipAddress));
    }

    public String ipRiskScore(String ipAddress) {
        return buildKey("geo", "risk", sanitizeIp(ipAddress));
    }

    public String geofence(String geofenceId) {
        return buildKey("geo", "geofence", geofenceId);
    }

    // ========== Attribution 相关 ==========

    public String attributionResult(String conversionId) {
        return buildKey("attribution", "result", conversionId);
    }

    public String touchPointsByUser(String userId) {
        return buildKey("attribution", "touchpoints", userId);
    }

    public String customerJourney(String userId) {
        return buildKey("attribution", "journey", userId);
    }

    // ========== Offer 相关 ==========

    public String offer(String offerId) {
        return buildKey("offer", offerId);
    }

    public String offersByAffiliate(String affiliateId) {
        return buildKey("offer", "affiliate", affiliateId);
    }

    public String offerCap(String offerId, String date) {
        return buildKey("offer", offerId, "cap", date);
    }

    public String offerApplication(String applicationId) {
        return buildKey("application", applicationId);
    }

    public String offerAccess(String offerId, String affiliateId) {
        return buildKey("application", "access", offerId, affiliateId);
    }

    // ========== Conversion 相关 ==========

    public String conversion(String conversionId) {
        return buildKey("conversion", conversionId);
    }

    public String conversionByClickId(String clickId) {
        return buildKey("conversion", "click", clickId);
    }

    public String conversionByTxId(String offerId, String txId) {
        return buildKey("conversion", "tx", offerId, txId);
    }

    // ========== Click Session 相关 ==========

    public String clickSession(String clickId) {
        return buildKey("click", "session", clickId);
    }

    public String clicksByAffiliate(String affiliateId, String date) {
        return buildKey("click", "affiliate", affiliateId, date);
    }

    // ========== Notification 相关 ==========

    public String notification(String notificationId) {
        return buildKey("notification", notificationId);
    }

    public String notificationUnreadCount(String affiliateId) {
        return buildKey("notification", "unread_count", affiliateId);
    }

    public String notificationPreference(String affiliateId, String type) {
        return buildKey("notification", "pref", affiliateId, type);
    }

    public String webhookEndpoint(String webhookId) {
        return buildKey("notification", "webhook", webhookId);
    }

    // ========== Creative 相关 ==========

    public String creative(String creativeId) {
        return buildKey("creative", creativeId);
    }

    public String creativesByOffer(String offerId) {
        return buildKey("creative", "offer", offerId);
    }

    public String creativePerformance(String creativeId) {
        return buildKey("creative", creativeId, "performance");
    }

    // ========== Referral 相关 ==========

    public String referralRelationship(String refereeId) {
        return buildKey("referral", "relationship", refereeId);
    }

    public String referralsByReferrer(String referrerId) {
        return buildKey("referral", "referrer", referrerId);
    }

    public String referralStats(String referrerId) {
        return buildKey("referral", "stats", referrerId);
    }

    // ========== Compliance 相关 ==========

    public String kycVerification(String affiliateId) {
        return buildKey("compliance", "kyc", affiliateId);
    }

    public String taxDocument(String documentId) {
        return buildKey("compliance", "tax", documentId);
    }

    public String taxCompliance(String affiliateId, String year) {
        return buildKey("compliance", "tax", affiliateId, year);
    }

    public String termsAcceptance(String affiliateId) {
        return buildKey("compliance", "terms", affiliateId);
    }

    public String complianceRiskScore(String affiliateId) {
        return buildKey("compliance", "risk", affiliateId);
    }

    // ========== Report 相关 ==========

    public String performanceReport(String requestHash) {
        return buildKey("report", "performance", requestHash);
    }

    public String subIdStats(String affiliateId, String subId) {
        return buildKey("report", "subid", affiliateId, subId);
    }

    // ========== SmartLink/TDS 相关 ==========

    public String smartLink(String smartLinkId) {
        return buildKey("smartlink", smartLinkId);
    }

    public String tdsEpc(String offerId, String country, String device) {
        return buildKey("tds", "epc", offerId, country, device);
    }

    // ========== Fraud 相关 ==========

    public String ipBlacklist(String ipAddress) {
        return buildKey("fraud", "blacklist", "ip", sanitizeIp(ipAddress));
    }

    public String subIdBlacklist(String affiliateId, String subId) {
        return buildKey("fraud", "blacklist", "subid", affiliateId, subId);
    }

    public String fraudAuditLog(String conversionId) {
        return buildKey("fraud", "audit", conversionId);
    }

    // ========== 辅助方法 ==========

    /**
     * 构建缓存键
     */
    private String buildKey(String... parts) {
        StringBuilder key = new StringBuilder(NAMESPACE);
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                key.append(SEPARATOR).append(part);
            }
        }
        return key.toString();
    }

    /**
     * 清理 IP 地址（替换点号为下划线）
     */
    private String sanitizeIp(String ipAddress) {
        return ipAddress.replace(".", "_").replace(":", "_");
    }

    /**
     * 生成模式匹配键（用于批量删除）
     */
    public String patternByPrefix(String... prefixParts) {
        return buildKey(prefixParts) + "*";
    }

    /**
     * 生成哈希键（用于复杂对象）
     */
    public String hashKey(String prefix, Object... params) {
        StringBuilder sb = new StringBuilder();
        for (Object param : params) {
            sb.append(param).append("_");
        }
        int hash = sb.toString().hashCode();
        return buildKey(prefix, String.valueOf(Math.abs(hash)));
    }
}
