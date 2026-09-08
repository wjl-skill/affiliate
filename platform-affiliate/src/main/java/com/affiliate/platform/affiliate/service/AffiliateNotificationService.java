package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.cache.CacheKeyGenerator;
import com.affiliate.platform.affiliate.cache.MultiLevelCacheManager;
import com.affiliate.platform.affiliate.domain.NotificationEntity;
import com.affiliate.platform.affiliate.domain.NotificationPreferenceEntity;
import com.affiliate.platform.affiliate.domain.WebhookEndpointEntity;
import com.affiliate.platform.affiliate.repository.NotificationPreferenceRepository;
import com.affiliate.platform.affiliate.repository.NotificationRepository;
import com.affiliate.platform.affiliate.repository.WebhookEndpointRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 网盟通知服务（重构版 - 接入 PostgreSQL + 多级缓存）
 * <p>
 * 功能：
 * 1. 转化通知（新转化、批准、拒绝）
 * 2. 支付通知（发票生成、付款完成、付款失败）
 * 3. Offer 更新通知（出价变化、暂停、恢复）
 * 4. 系统通知（账户升级、违规警告、审计提醒）
 * 5. 多渠道发送：Email、SMS、Webhook、站内消息
 */
@Service
public class AffiliateNotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final WebhookEndpointRepository webhookRepository;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    private static final Duration NOTIFICATION_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration PREFERENCE_CACHE_TTL = Duration.ofHours(1);
    private static final Duration WEBHOOK_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration UNREAD_COUNT_CACHE_TTL = Duration.ofMinutes(2);

    public AffiliateNotificationService(
            NotificationRepository notificationRepository,
            NotificationPreferenceRepository preferenceRepository,
            WebhookEndpointRepository webhookRepository,
            MultiLevelCacheManager cacheManager,
            CacheKeyGenerator keyGenerator,
            ObjectMapper objectMapper
    ) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.webhookRepository = webhookRepository;
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送转化通知
     */
    public void notifyConversion(
            String affiliateId,
            String offerId,
            String clickId,
            String txId,
            ConversionStatus status,
            String revenue
    ) {
        String message = switch (status) {
            case NEW -> String.format("New conversion tracked for Offer %s (TxID: %s, Revenue: $%s)", offerId, txId, revenue);
            case APPROVED -> String.format("Conversion approved! TxID: %s, Payout: $%s", txId, revenue);
            case REJECTED -> String.format("Conversion rejected. TxID: %s, Reason: Quality check failed", txId);
        };

        sendNotification(
                affiliateId,
                NotificationType.CONVERSION,
                "Conversion Update",
                message,
                NotificationPriority.NORMAL,
                Map.of(
                        "offerId", offerId,
                        "clickId", clickId,
                        "txId", txId,
                        "status", status.name(),
                        "revenue", revenue
                )
        );
    }

    /**
     * 发送支付通知
     */
    public void notifyPayment(
            String affiliateId,
            String invoiceId,
            PaymentStatus status,
            String amount,
            String paymentMethod,
            String failureReason
    ) {
        String message = switch (status) {
            case INVOICE_GENERATED -> String.format("Invoice %s generated for $%s. Review in dashboard.", invoiceId, amount);
            case PAYMENT_PROCESSING -> String.format("Payment of $%s is being processed via %s", amount, paymentMethod);
            case PAYMENT_COMPLETED -> String.format("Payment successful! $%s sent via %s", amount, paymentMethod);
            case PAYMENT_FAILED -> String.format("Payment failed: %s. Please update payment method.", failureReason);
        };

        sendNotification(
                affiliateId,
                NotificationType.PAYMENT,
                "Payment Update",
                message,
                status == PaymentStatus.PAYMENT_FAILED ? NotificationPriority.HIGH : NotificationPriority.NORMAL,
                Map.of(
                        "invoiceId", invoiceId,
                        "status", status.name(),
                        "amount", amount,
                        "paymentMethod", paymentMethod != null ? paymentMethod : "",
                        "failureReason", failureReason != null ? failureReason : ""
                )
        );
    }

    /**
     * 发送 Offer 更新通知
     */
    public void notifyOfferUpdate(
            String affiliateId,
            String offerId,
            OfferUpdateType updateType,
            String details
    ) {
        String message = switch (updateType) {
            case PAYOUT_INCREASED -> String.format("Great news! Payout for Offer %s increased. %s", offerId, details);
            case PAYOUT_DECREASED -> String.format("Notice: Payout for Offer %s decreased. %s", offerId, details);
            case PAUSED -> String.format("Offer %s temporarily paused. %s", offerId, details);
            case RESUMED -> String.format("Offer %s is now active again!", offerId);
            case CAP_REACHED -> String.format("Daily cap reached for Offer %s. Will reset at midnight UTC.", offerId);
            case EXPIRING_SOON -> String.format("Offer %s expires in 7 days. Maximize your earnings now!", offerId);
        };

        sendNotification(
                affiliateId,
                NotificationType.OFFER_UPDATE,
                "Offer Update",
                message,
                updateType == OfferUpdateType.PAUSED ? NotificationPriority.HIGH : NotificationPriority.NORMAL,
                Map.of(
                        "offerId", offerId,
                        "updateType", updateType.name(),
                        "details", details
                )
        );
    }

    /**
     * 发送系统通知
     */
    public void notifySystem(
            String affiliateId,
            SystemNotificationType type,
            String title,
            String message,
            NotificationPriority priority
    ) {
        sendNotification(
                affiliateId,
                NotificationType.SYSTEM,
                title,
                message,
                priority,
                Map.of("systemType", type.name())
        );
    }

    /**
     * 发送欺诈警告
     */
    public void notifyFraudAlert(
            String affiliateId,
            String clickId,
            String conversionId,
            String reason,
            int riskScore
    ) {
        sendNotification(
                affiliateId,
                NotificationType.FRAUD_ALERT,
                "Fraud Alert - Action Required",
                String.format("Suspicious activity detected. Risk Score: %d/100. Reason: %s. Conversion: %s",
                        riskScore, reason, conversionId),
                NotificationPriority.URGENT,
                Map.of(
                        "clickId", clickId,
                        "conversionId", conversionId,
                        "reason", reason,
                        "riskScore", String.valueOf(riskScore)
                )
        );
    }

    /**
     * 批量通知（广播）
     */
    @Transactional
    public void broadcastToAll(
            NotificationType type,
            String title,
            String message,
            NotificationPriority priority
    ) {
        // TODO: 从 AffiliatePartnerService 获取所有活跃渠道
        List<String> allAffiliateIds = List.of(); // 简化实现

        for (String affiliateId : allAffiliateIds) {
            sendNotification(affiliateId, type, title, message, priority, Map.of());
        }
    }

    /**
     * 核心发送逻辑
     */
    @Transactional
    public void sendNotification(
            String recipientId,
            NotificationType type,
            String title,
            String message,
            NotificationPriority priority,
            Map<String, String> metadata
    ) {
        String notificationId = generateNotificationId();
        NotificationEntity entity = new NotificationEntity(
                notificationId,
                recipientId,
                type.name(),
                title,
                message,
                priority.name(),
                serializeMap(metadata),
                false,
                null,
                Instant.now()
        );

        notificationRepository.save(entity);

        // 失效缓存
        cacheManager.evictByPattern("affiliate:notification:recipient:" + recipientId + ":*");
        cacheManager.evict(keyGenerator.notificationUnreadCount(recipientId));

        // 根据用户偏好选择发送渠道
        NotificationPreference pref = getUserPreference(recipientId, type);

        if (pref.enableEmail()) {
            sendEmail(recipientId, title, message);
        }

        if (pref.enableSms() && priority.ordinal() >= NotificationPriority.HIGH.ordinal()) {
            sendSms(recipientId, message);
        }

        if (pref.enableWebhook()) {
            sendWebhook(recipientId, toNotification(entity));
        }

        // 站内消息始终保存
    }

    /**
     * 获取用户通知偏好（带缓存）
     */
    public NotificationPreference getUserPreference(String affiliateId, NotificationType type) {
        String cacheKey = "affiliate:notification:preference:" + affiliateId + ":" + type.name();

        return cacheManager.get(
                cacheKey,
                NotificationPreferenceEntity.class,
                PREFERENCE_CACHE_TTL,
                () -> preferenceRepository.findByAffiliateIdAndNotificationType(
                        affiliateId, type.name()).orElse(null)
        ).map(this::toNotificationPreference)
                .orElse(new NotificationPreference(
                        type,
                        true,  // Email 默认开启
                        false, // SMS 默认关闭
                        false, // Webhook 默认关闭
                        true   // 站内消息默认开启
                ));
    }

    /**
     * 更新通知偏好
     */
    @Transactional
    public void updatePreference(String affiliateId, NotificationPreference preference) {
        Optional<NotificationPreferenceEntity> existing = preferenceRepository
                .findByAffiliateIdAndNotificationType(affiliateId, preference.type().name());

        NotificationPreferenceEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setEnableEmail(preference.enableEmail());
            entity.setEnableSms(preference.enableSms());
            entity.setEnableWebhook(preference.enableWebhook());
            entity.setEnableInApp(preference.enableInApp());
        } else {
            String prefId = UUID.randomUUID().toString();
            entity = new NotificationPreferenceEntity(
                    prefId,
                    affiliateId,
                    preference.type().name(),
                    preference.enableEmail(),
                    preference.enableSms(),
                    preference.enableWebhook(),
                    preference.enableInApp(),
                    Instant.now(),
                    null
            );
        }

        preferenceRepository.save(entity);

        // 失效缓存
        cacheManager.evict("affiliate:notification:preference:" + affiliateId + ":" + preference.type().name());
    }

    /**
     * 注册 Webhook 端点
     */
    @Transactional
    public WebhookEndpoint registerWebhook(
            String affiliateId,
            String url,
            String secret,
            List<NotificationType> subscribedTypes
    ) {
        String webhookId = UUID.randomUUID().toString();
        WebhookEndpointEntity entity = new WebhookEndpointEntity(
                webhookId,
                affiliateId,
                url,
                secret,
                serializeNotificationTypes(subscribedTypes),
                true,
                0,
                null,
                null,
                Instant.now(),
                null
        );

        webhookRepository.save(entity);

        // 失效缓存
        cacheManager.evictByPattern("affiliate:notification:webhook:" + affiliateId + ":*");

        return toWebhookEndpoint(entity);
    }

    /**
     * 获取 Webhook 端点列表（带缓存）
     */
    public List<WebhookEndpoint> getWebhookEndpoints(String affiliateId) {
        String cacheKey = "affiliate:notification:webhook:" + affiliateId + ":all";

        return cacheManager.get(
                cacheKey,
                List.class,
                WEBHOOK_CACHE_TTL,
                () -> webhookRepository.findByAffiliateId(affiliateId)
        ).map(list -> ((List<WebhookEndpointEntity>) list).stream()
                .map(this::toWebhookEndpoint)
                .collect(Collectors.toList())
        ).orElse(List.of());
    }

    /**
     * 获取未读通知（带缓存）
     */
    public List<Notification> getUnreadNotifications(String affiliateId, int limit) {
        String cacheKey = "affiliate:notification:recipient:" + affiliateId + ":unread:limit:" + limit;

        return cacheManager.get(
                cacheKey,
                List.class,
                NOTIFICATION_CACHE_TTL,
                () -> notificationRepository.findByRecipientIdAndIsReadOrderByCreatedAtDesc(affiliateId, false)
                        .stream()
                        .limit(limit)
                        .collect(Collectors.toList())
        ).map(list -> ((List<NotificationEntity>) list).stream()
                .map(this::toNotification)
                .collect(Collectors.toList())
        ).orElse(List.of());
    }

    /**
     * 获取未读通知数量（带缓存）
     */
    public long getUnreadCount(String affiliateId) {
        String cacheKey = keyGenerator.notificationUnreadCount(affiliateId);

        return cacheManager.get(
                cacheKey,
                Long.class,
                UNREAD_COUNT_CACHE_TTL,
                () -> notificationRepository.countByRecipientIdAndIsRead(affiliateId, false)
        ).orElse(0L);
    }

    /**
     * 标记为已读
     */
    @Transactional
    public void markAsRead(String notificationId) {
        Optional<NotificationEntity> optEntity = notificationRepository.findById(notificationId);
        if (optEntity.isPresent()) {
            NotificationEntity entity = optEntity.get();
            entity.setIsRead(true);
            entity.setReadAt(Instant.now());
            notificationRepository.save(entity);

            // 失效缓存
            cacheManager.evictByPattern("affiliate:notification:recipient:" + entity.getRecipientId() + ":*");
            cacheManager.evict(keyGenerator.notificationUnreadCount(entity.getRecipientId()));
        }
    }

    /**
     * 批量标记已读
     */
    @Transactional
    public void markAllAsRead(String affiliateId) {
        notificationRepository.markAllAsRead(affiliateId, Instant.now());

        // 失效缓存
        cacheManager.evictByPattern("affiliate:notification:recipient:" + affiliateId + ":*");
        cacheManager.evict(keyGenerator.notificationUnreadCount(affiliateId));
    }

    /**
     * 获取通知历史
     */
    public List<Notification> getNotificationHistory(
            String affiliateId,
            NotificationType type,
            Instant from,
            Instant to,
            int limit
    ) {
        List<NotificationEntity> entities;

        if (type != null) {
            entities = notificationRepository.findByRecipientIdAndTypeAndDateRange(
                    affiliateId, type.name(), from, to);
        } else {
            entities = notificationRepository.findByRecipientIdAndDateRange(
                    affiliateId, from, to);
        }

        return entities.stream()
                .limit(limit)
                .map(this::toNotification)
                .collect(Collectors.toList());
    }

    /**
     * 删除通知
     */
    @Transactional
    public void deleteNotification(String notificationId) {
        Optional<NotificationEntity> optEntity = notificationRepository.findById(notificationId);
        if (optEntity.isPresent()) {
            NotificationEntity entity = optEntity.get();
            notificationRepository.deleteById(notificationId);

            // 失效缓存
            cacheManager.evictByPattern("affiliate:notification:recipient:" + entity.getRecipientId() + ":*");
            cacheManager.evict(keyGenerator.notificationUnreadCount(entity.getRecipientId()));
        }
    }

    /**
     * 清理旧通知（定时任务）
     */
    @Transactional
    public int cleanupOldNotifications(int daysToKeep) {
        Instant before = Instant.now().minus(Duration.ofDays(daysToKeep));
        int deleted = notificationRepository.deleteOldNotifications(before);

        // 失效所有通知缓存
        cacheManager.evictByPattern("affiliate:notification:*");

        return deleted;
    }

    // ========== 私有发送方法（集成外部服务） ==========

    private void sendEmail(String recipientId, String subject, String body) {
        // TODO: 集成 SendGrid / AWS SES
        System.out.println("[Email] To: " + recipientId + ", Subject: " + subject);
    }

    private void sendSms(String recipientId, String message) {
        // TODO: 集成 Twilio / AWS SNS
        System.out.println("[SMS] To: " + recipientId + ", Message: " + message);
    }

    private void sendWebhook(String recipientId, Notification notification) {
        List<WebhookEndpoint> endpoints = getWebhookEndpoints(recipientId);

        for (WebhookEndpoint endpoint : endpoints) {
            if (!endpoint.active() || !endpoint.subscribedTypes().contains(notification.type())) {
                continue;
            }

            // TODO: HTTP POST with signature
            // 1. JSON 序列化 notification
            // 2. HMAC-SHA256 签名
            // 3. 异步 HTTP POST
            // 4. 失败重试（指数退避）
            // 5. 超过 3 次失败自动禁用

            System.out.println("[Webhook] " + endpoint.url() + " <- " + notification.title());
        }
    }

    // ========== 私有辅助方法 ==========

    private String generateNotificationId() {
        return "notif_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String serializeMap(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String serializeNotificationTypes(List<NotificationType> types) {
        try {
            List<String> typeNames = types.stream().map(Enum::name).collect(Collectors.toList());
            return objectMapper.writeValueAsString(typeNames);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private Map<String, String> deserializeMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private List<NotificationType> deserializeNotificationTypes(String json) {
        try {
            List<String> typeNames = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return typeNames.stream()
                    .map(NotificationType::valueOf)
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Notification toNotification(NotificationEntity entity) {
        return new Notification(
                entity.getId(),
                entity.getRecipientId(),
                NotificationType.valueOf(entity.getType()),
                entity.getTitle(),
                entity.getMessage(),
                NotificationPriority.valueOf(entity.getPriority()),
                deserializeMap(entity.getMetadata()),
                entity.getIsRead(),
                entity.getCreatedAt()
        );
    }

    private NotificationPreference toNotificationPreference(NotificationPreferenceEntity entity) {
        return new NotificationPreference(
                NotificationType.valueOf(entity.getNotificationType()),
                entity.getEnableEmail(),
                entity.getEnableSms(),
                entity.getEnableWebhook(),
                entity.getEnableInApp()
        );
    }

    private WebhookEndpoint toWebhookEndpoint(WebhookEndpointEntity entity) {
        return new WebhookEndpoint(
                entity.getId(),
                entity.getAffiliateId(),
                entity.getUrl(),
                entity.getSecret(),
                deserializeNotificationTypes(entity.getSubscribedTypes()),
                entity.getActive(),
                entity.getFailureCount(),
                entity.getLastFailedAt(),
                entity.getCreatedAt()
        );
    }

    // ========== 数据记录 ==========

    public record Notification(
            String id,
            String recipientId,
            NotificationType type,
            String title,
            String message,
            NotificationPriority priority,
            Map<String, String> metadata,
            boolean isRead,
            Instant createdAt
    ) {}

    public record NotificationPreference(
            NotificationType type,
            boolean enableEmail,
            boolean enableSms,
            boolean enableWebhook,
            boolean enableInApp
    ) {}

    public record WebhookEndpoint(
            String id,
            String affiliateId,
            String url,
            String secret,
            List<NotificationType> subscribedTypes,
            boolean active,
            int failureCount,
            Instant lastFailedAt,
            Instant createdAt
    ) {}

    public enum NotificationType {
        CONVERSION,       // 转化通知
        PAYMENT,          // 支付通知
        OFFER_UPDATE,     // Offer 更新
        SYSTEM,           // 系统通知
        FRAUD_ALERT,      // 欺诈警告
        ACCOUNT,          // 账户变动
        MARKETING         // 营销推广
    }

    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum ConversionStatus {
        NEW,
        APPROVED,
        REJECTED
    }

    public enum PaymentStatus {
        INVOICE_GENERATED,
        PAYMENT_PROCESSING,
        PAYMENT_COMPLETED,
        PAYMENT_FAILED
    }

    public enum OfferUpdateType {
        PAYOUT_INCREASED,
        PAYOUT_DECREASED,
        PAUSED,
        RESUMED,
        CAP_REACHED,
        EXPIRING_SOON
    }

    public enum SystemNotificationType {
        TIER_UPGRADED,      // 等级升级
        QUALITY_SCORE_CHANGE, // 质量分变化
        COMPLIANCE_REMINDER,  // 合规提醒
        TAX_FORM_REQUIRED,    // 税表需要
        TERMS_UPDATED,        // 条款更新
        MAINTENANCE_SCHEDULED // 系统维护
    }
}
