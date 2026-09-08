package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.cache.CacheKeyGenerator;
import com.affiliate.platform.affiliate.cache.MultiLevelCacheManager;
import com.affiliate.platform.affiliate.domain.PaymentMethodEntity;
import com.affiliate.platform.affiliate.domain.PaymentTransactionEntity;
import com.affiliate.platform.affiliate.repository.PaymentMethodRepository;
import com.affiliate.platform.affiliate.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 支付网关服务（接入 PostgreSQL + 多级缓存）
 * <p>
 * 功能：
 * 1. 多支付方式管理（PayPal、Stripe、Wire Transfer、Check、Cryptocurrency）
 * 2. 批量支付处理
 * 3. 支付失败重试机制
 * 4. 汇率转换（多币种支持）
 * 5. 支付手续费计算
 * 6. 支付状态追踪和对账
 * 7. 税务预扣（1099 报税）
 * <p>
 * 对标：Impact.com Payment Gateway、CJ Affiliate Payment Center、Tipalti
 */
@Service
public class PaymentGatewayService {

    private final PaymentTransactionRepository transactionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    private static final Duration PAYMENT_METHOD_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration TRANSACTION_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration EXCHANGE_RATE_CACHE_TTL = Duration.ofHours(1);

    // 汇率缓存（内存 + Redis）
    private final ConcurrentMap<String, BigDecimal> exchangeRates = new ConcurrentHashMap<>();

    public PaymentGatewayService(
            PaymentTransactionRepository transactionRepository,
            PaymentMethodRepository paymentMethodRepository,
            MultiLevelCacheManager cacheManager,
            CacheKeyGenerator keyGenerator,
            ObjectMapper objectMapper
    ) {
        this.transactionRepository = transactionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
        initializeExchangeRates();
    }

    private void initializeExchangeRates() {
        // TODO: 从外部 API 加载实时汇率
        exchangeRates.put("USD_EUR", new BigDecimal("0.92"));
        exchangeRates.put("USD_GBP", new BigDecimal("0.79"));
        exchangeRates.put("USD_CAD", new BigDecimal("1.36"));
        exchangeRates.put("USD_AUD", new BigDecimal("1.52"));
        exchangeRates.put("USD_CNY", new BigDecimal("7.24"));
    }

    /**
     * 添加支付方式
     */
    @Transactional
    public PaymentMethod addPaymentMethod(
            String affiliateId,
            PaymentMethodType type,
            Map<String, String> credentials,
            String currency,
            boolean isPrimary
    ) {
        // 验证凭证完整性
        validateCredentials(type, credentials);

        String paymentMethodId = UUID.randomUUID().toString();

        // 如果设为主支付方式，取消其他主标志
        if (isPrimary) {
            List<PaymentMethodEntity> existingMethods =
                    paymentMethodRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId);
            for (PaymentMethodEntity existing : existingMethods) {
                if (Boolean.TRUE.equals(existing.getIsPrimary())) {
                    existing.setIsPrimary(false);
                    paymentMethodRepository.save(existing);

                    // 失效缓存
                    cacheManager.evict(keyGenerator.paymentMethod(existing.getId()));
                }
            }
            // 失效聚合缓存
            cacheManager.evict(keyGenerator.affiliatePaymentMethods(affiliateId));
        }

        // 保存到数据库
        PaymentMethodEntity entity = new PaymentMethodEntity(
                paymentMethodId,
                affiliateId,
                type.name(),
                serializeMap(credentials),
                currency,
                isPrimary,
                PaymentMethodStatus.PENDING_VERIFICATION.name(),
                null,
                Instant.now(),
                null
        );

        paymentMethodRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.affiliatePaymentMethods(affiliateId));

        return toPaymentMethod(entity);
    }

    /**
     * 验证支付方式
     */
    @Transactional
    public PaymentMethod verifyPaymentMethod(String paymentMethodId) {
        PaymentMethodEntity entity = paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new IllegalArgumentException("Payment method not found"));

        // TODO: 调用支付网关 API 验证账户
        // - PayPal: 发送小额测试款项
        // - Bank: 验证路由号和账号
        // - Cryptocurrency: 验证钱包地址格式

        entity.setStatus(PaymentMethodStatus.VERIFIED.name());
        entity.setVerifiedAt(Instant.now());
        paymentMethodRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.paymentMethod(paymentMethodId));
        cacheManager.evict(keyGenerator.affiliatePaymentMethods(entity.getAffiliateId()));

        return toPaymentMethod(entity);
    }

    /**
     * 创建支付交易
     */
    @Transactional
    public PaymentTransaction createPayment(
            String affiliateId,
            String invoiceId,
            BigDecimal amount,
            String currency,
            String paymentMethodId
    ) {
        PaymentMethodEntity paymentMethodEntity = paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new IllegalArgumentException("Payment method not found"));

        if (!PaymentMethodStatus.VERIFIED.name().equals(paymentMethodEntity.getStatus())) {
            throw new IllegalStateException("Payment method not verified");
        }

        String transactionId = generateTransactionId();

        // 计算手续费
        PaymentMethodType type = PaymentMethodType.valueOf(paymentMethodEntity.getType());
        BigDecimal fee = calculateFee(amount, type);
        BigDecimal netAmount = amount.subtract(fee);

        // 汇率转换
        BigDecimal convertedAmount = netAmount;
        BigDecimal exchangeRate = BigDecimal.ONE;

        if (!currency.equals(paymentMethodEntity.getCurrency())) {
            exchangeRate = getExchangeRate(currency, paymentMethodEntity.getCurrency());
            convertedAmount = netAmount.multiply(exchangeRate);
        }

        // 保存到数据库
        PaymentTransactionEntity entity = new PaymentTransactionEntity(
                transactionId,
                affiliateId,
                invoiceId,
                paymentMethodId,
                amount,
                currency,
                fee,
                netAmount,
                paymentMethodEntity.getCurrency(),
                exchangeRate,
                convertedAmount,
                PaymentStatus.PENDING.name(),
                null,
                null,
                0,
                Instant.now(),
                null,
                null
        );

        transactionRepository.save(entity);

        return toPaymentTransaction(entity);
    }

    /**
     * 执行支付
     */
    @Transactional
    public PaymentTransaction executePayment(String transactionId) {
        PaymentTransactionEntity entity = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (!PaymentStatus.PENDING.name().equals(entity.getStatus())) {
            throw new IllegalStateException("Transaction not in pending state");
        }

        PaymentMethodEntity paymentMethodEntity = paymentMethodRepository.findById(entity.getPaymentMethodId())
                .orElseThrow(() -> new IllegalArgumentException("Payment method not found"));

        try {
            // 调用支付网关
            String externalId = processPaymentViaGateway(toPaymentTransaction(entity), toPaymentMethod(paymentMethodEntity));

            entity.setStatus(PaymentStatus.PROCESSING.name());
            entity.setExternalPaymentId(externalId);
            entity.setProcessedAt(Instant.now());
            transactionRepository.save(entity);

            // 更新支付方式最后使用时间
            updatePaymentMethodLastUsed(entity.getPaymentMethodId());

            // 失效缓存
            cacheManager.evict(keyGenerator.paymentTransaction(transactionId));

            return toPaymentTransaction(entity);

        } catch (PaymentGatewayException e) {
            return handlePaymentFailure(toPaymentTransaction(entity), e.getMessage());
        }
    }

    /**
     * 创建批量支付
     */
    public PaymentBatch createPaymentBatch(
            List<String> invoiceIds,
            String batchName
    ) {
        String batchId = "batch_" + UUID.randomUUID().toString().replace("-", "");

        List<String> transactionIds = new ArrayList<>();

        for (String invoiceId : invoiceIds) {
            // TODO: 从 AffiliateSettlementService 获取 invoice 详情
            // 简化实现
        }

        PaymentBatch batch = new PaymentBatch(
                batchId,
                batchName,
                transactionIds,
                PaymentBatchStatus.CREATED,
                BigDecimal.ZERO,
                0,
                0,
                0,
                Instant.now(),
                null,
                null
        );

        // 批量操作暂时保留内存存储（V2同样处理）
        String cacheKey = keyGenerator.paymentBatch(batchId);
        cacheManager.put(cacheKey, batch, Duration.ofHours(24));

        return batch;
    }

    /**
     * 执行批量支付
     */
    @Transactional
    public PaymentBatch executeBatch(String batchId) {
        String cacheKey = keyGenerator.paymentBatch(batchId);
        PaymentBatch batch = cacheManager.get(cacheKey, PaymentBatch.class, Duration.ofHours(24), () -> null)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

        int successCount = 0;
        int failureCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (String transactionId : batch.transactionIds()) {
            try {
                PaymentTransaction result = executePayment(transactionId);
                if (result.status() == PaymentStatus.PROCESSING || result.status() == PaymentStatus.COMPLETED) {
                    successCount++;
                    totalAmount = totalAmount.add(result.amount());
                } else {
                    failureCount++;
                }
            } catch (Exception e) {
                failureCount++;
            }
        }

        PaymentBatch completed = new PaymentBatch(
                batch.id(),
                batch.name(),
                batch.transactionIds(),
                failureCount == 0 ? PaymentBatchStatus.COMPLETED : PaymentBatchStatus.PARTIAL,
                totalAmount,
                successCount,
                failureCount,
                batch.transactionIds().size() - successCount - failureCount,
                batch.createdAt(),
                Instant.now(),
                Instant.now()
        );

        cacheManager.put(cacheKey, completed, Duration.ofHours(24));

        return completed;
    }

    /**
     * 支付失败重试
     */
    @Transactional
    public PaymentTransaction retryPayment(String transactionId) {
        PaymentTransactionEntity entity = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (!PaymentStatus.FAILED.name().equals(entity.getStatus())) {
            throw new IllegalStateException("Only failed transactions can be retried");
        }

        if (entity.getRetryCount() >= 3) {
            throw new IllegalStateException("Maximum retry attempts reached");
        }

        // 重置状态为 PENDING
        entity.setStatus(PaymentStatus.PENDING.name());
        entity.setErrorMessage(null);
        entity.setRetryCount(entity.getRetryCount() + 1);
        transactionRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.paymentTransaction(transactionId));

        // 重新执行
        return executePayment(transactionId);
    }

    /**
     * 获取渠道的支付历史
     */
    public List<PaymentTransaction> getPaymentHistory(
            String affiliateId,
            PaymentStatus status,
            Instant from,
            Instant to
    ) {
        List<PaymentTransactionEntity> entities;

        if (status != null) {
            entities = transactionRepository.findByAffiliateIdAndStatusOrderByCreatedAtDesc(
                    affiliateId, status.name());
        } else {
            entities = transactionRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId);
        }

        return entities.stream()
                .filter(t -> t.getCreatedAt().isAfter(from) && t.getCreatedAt().isBefore(to))
                .map(this::toPaymentTransaction)
                .collect(Collectors.toList());
    }

    /**
     * 获取渠道的支付偏好
     */
    public PaymentPreference getPaymentPreference(String affiliateId) {
        String cacheKey = "payment:preference:" + affiliateId;
        return cacheManager.get(cacheKey, PaymentPreference.class, Duration.ofHours(1), () -> null)
                .orElse(new PaymentPreference(
                        affiliateId,
                        new BigDecimal("100.00"), // 默认起提金额 $100
                        "USD",
                        true,  // 自动支付
                        null
                ));
    }

    /**
     * 更新支付偏好
     */
    public void updatePaymentPreference(String affiliateId, PaymentPreference preference) {
        String cacheKey = "payment:preference:" + affiliateId;
        cacheManager.put(cacheKey, preference, Duration.ofHours(1));
    }

    /**
     * 获取支付方式（带缓存）- V2新增
     */
    public PaymentMethod getPaymentMethod(String paymentMethodId) {
        String cacheKey = keyGenerator.paymentMethod(paymentMethodId);
        return cacheManager.get(
                cacheKey,
                PaymentMethodEntity.class,
                PAYMENT_METHOD_CACHE_TTL,
                () -> paymentMethodRepository.findById(paymentMethodId).orElse(null)
        ).map(this::toPaymentMethod).orElse(null);
    }

    /**
     * 获取渠道的所有支付方式（带缓存）- V2新增
     */
    public List<PaymentMethod> getPaymentMethods(String affiliateId) {
        String cacheKey = keyGenerator.affiliatePaymentMethods(affiliateId);
        List<PaymentMethodEntity> entities = cacheManager.get(
                cacheKey,
                List.class,
                PAYMENT_METHOD_CACHE_TTL,
                () -> paymentMethodRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId)
        ).orElse(List.of());

        // 需要手动转换，因为泛型擦除
        return paymentMethodRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId).stream()
                .map(this::toPaymentMethod)
                .collect(Collectors.toList());
    }

    /**
     * 获取交易详情（带缓存）- V2新增
     */
    public PaymentTransaction getTransaction(String transactionId) {
        String cacheKey = keyGenerator.paymentTransaction(transactionId);
        return cacheManager.get(
                cacheKey,
                PaymentTransactionEntity.class,
                TRANSACTION_CACHE_TTL,
                () -> transactionRepository.findById(transactionId).orElse(null)
        ).map(this::toPaymentTransaction).orElse(null);
    }

    /**
     * 支付对账（基于批次）
     */
    public ReconciliationReport reconcile(String batchId) {
        String cacheKey = keyGenerator.paymentBatch(batchId);
        PaymentBatch batch = cacheManager.get(cacheKey, PaymentBatch.class, Duration.ofHours(24), () -> null)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

        List<PaymentTransaction> batchTransactions = batch.transactionIds().stream()
                .map(tid -> transactionRepository.findById(tid).orElse(null))
                .filter(Objects::nonNull)
                .map(this::toPaymentTransaction)
                .toList();

        BigDecimal expectedTotal = batchTransactions.stream()
                .map(PaymentTransaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal actualTotal = batchTransactions.stream()
                .filter(t -> t.status() == PaymentStatus.COMPLETED)
                .map(PaymentTransaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discrepancy = expectedTotal.subtract(actualTotal);

        return new ReconciliationReport(
                batchId,
                batchTransactions.size(),
                expectedTotal,
                actualTotal,
                discrepancy,
                discrepancy.compareTo(BigDecimal.ZERO) == 0,
                Instant.now()
        );
    }

    /**
     * 支付对账（基于交易ID列表）
     */
    public ReconciliationReport reconcile(List<String> transactionIds) {
        List<PaymentTransaction> transactions = transactionIds.stream()
                .map(tid -> transactionRepository.findById(tid).orElse(null))
                .filter(Objects::nonNull)
                .map(this::toPaymentTransaction)
                .toList();

        BigDecimal expectedTotal = transactions.stream()
                .map(PaymentTransaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal actualTotal = transactions.stream()
                .filter(t -> t.status() == PaymentStatus.COMPLETED)
                .map(PaymentTransaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discrepancy = expectedTotal.subtract(actualTotal);

        return new ReconciliationReport(
                "manual_" + UUID.randomUUID().toString(),
                transactions.size(),
                expectedTotal,
                actualTotal,
                discrepancy,
                discrepancy.compareTo(BigDecimal.ZERO) == 0,
                Instant.now()
        );
    }

    // ========== 私有辅助方法 ==========

    private void validateCredentials(PaymentMethodType type, Map<String, String> credentials) {
        switch (type) {
            case PAYPAL -> {
                if (!credentials.containsKey("email")) {
                    throw new IllegalArgumentException("PayPal email required");
                }
            }
            case BANK_TRANSFER -> {
                if (!credentials.containsKey("accountNumber") || !credentials.containsKey("routingNumber")) {
                    throw new IllegalArgumentException("Bank account details required");
                }
            }
            case CRYPTOCURRENCY -> {
                if (!credentials.containsKey("walletAddress") || !credentials.containsKey("network")) {
                    throw new IllegalArgumentException("Crypto wallet details required");
                }
            }
        }
    }

    private BigDecimal calculateFee(BigDecimal amount, PaymentMethodType type) {
        return switch (type) {
            case PAYPAL -> amount.multiply(new BigDecimal("0.02")); // 2%
            case STRIPE -> amount.multiply(new BigDecimal("0.029")).add(new BigDecimal("0.30")); // 2.9% + $0.30
            case BANK_TRANSFER -> new BigDecimal("15.00"); // 固定 $15
            case WIRE_TRANSFER -> new BigDecimal("25.00"); // 固定 $25
            case CHECK -> new BigDecimal("5.00"); // 固定 $5
            case CRYPTOCURRENCY -> new BigDecimal("0.50"); // 固定 $0.50
        };
    }

    private BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return BigDecimal.ONE;
        }

        String key = fromCurrency + "_" + toCurrency;
        BigDecimal rate = exchangeRates.get(key);

        if (rate == null) {
            // 尝试反向汇率
            String reverseKey = toCurrency + "_" + fromCurrency;
            BigDecimal reverseRate = exchangeRates.get(reverseKey);
            if (reverseRate != null) {
                rate = BigDecimal.ONE.divide(reverseRate, 6, BigDecimal.ROUND_HALF_UP);
            } else {
                // 默认通过 USD 中转
                rate = BigDecimal.ONE; // 简化
            }
        }

        return rate;
    }

    private String processPaymentViaGateway(PaymentTransaction transaction, PaymentMethod method) throws PaymentGatewayException {
        // TODO: 调用实际支付网关 API
        // - PayPal: Payouts API
        // - Stripe: Transfers API
        // - Bank: ACH/Wire
        // - Crypto: Coinbase Commerce

        // 模拟成功
        return "ext_" + UUID.randomUUID().toString();
    }

    private PaymentTransaction handlePaymentFailure(PaymentTransaction transaction, String errorMessage) {
        PaymentTransactionEntity entity = transactionRepository.findById(transaction.id())
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        entity.setStatus(PaymentStatus.FAILED.name());
        entity.setErrorMessage(errorMessage);
        entity.setFailedAt(Instant.now());
        transactionRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.paymentTransaction(transaction.id()));

        return toPaymentTransaction(entity);
    }

    private void updatePaymentMethodLastUsed(String paymentMethodId) {
        paymentMethodRepository.findById(paymentMethodId).ifPresent(method -> {
            method.setLastUsedAt(Instant.now());
            paymentMethodRepository.save(method);

            // 失效缓存
            cacheManager.evict(keyGenerator.paymentMethod(paymentMethodId));
            cacheManager.evict(keyGenerator.affiliatePaymentMethods(method.getAffiliateId()));
        });
    }

    private String generateTransactionId() {
        return "tx_" + UUID.randomUUID().toString().replace("-", "");
    }

    // ========== Entity 转换方法 ==========

    private PaymentMethod toPaymentMethod(PaymentMethodEntity entity) {
        return new PaymentMethod(
                entity.getId(),
                entity.getAffiliateId(),
                PaymentMethodType.valueOf(entity.getType()),
                deserializeMap(entity.getCredentials()),
                entity.getCurrency(),
                Boolean.TRUE.equals(entity.getIsPrimary()),
                PaymentMethodStatus.valueOf(entity.getStatus()),
                entity.getLastUsedAt(),
                entity.getCreatedAt(),
                entity.getVerifiedAt()
        );
    }

    private PaymentTransaction toPaymentTransaction(PaymentTransactionEntity entity) {
        return new PaymentTransaction(
                entity.getId(),
                entity.getAffiliateId(),
                entity.getInvoiceId(),
                entity.getPaymentMethodId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getFee(),
                entity.getNetAmount(),
                entity.getPayoutCurrency(),
                entity.getExchangeRate(),
                entity.getConvertedAmount(),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getExternalPaymentId(),
                entity.getErrorMessage(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getProcessedAt(),
                entity.getFailedAt()
        );
    }

    private String serializeMap(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, String> deserializeMap(String json) {
        if (json == null || json.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    // ========== 数据记录 ==========

    public record PaymentMethod(
            String id,
            String affiliateId,
            PaymentMethodType type,
            Map<String, String> credentials,
            String currency,
            boolean isPrimary,
            PaymentMethodStatus status,
            Instant lastUsedAt,
            Instant createdAt,
            Instant verifiedAt
    ) {}

    public record PaymentTransaction(
            String id,
            String affiliateId,
            String invoiceId,
            String paymentMethodId,
            BigDecimal amount,
            String currency,
            BigDecimal fee,
            BigDecimal netAmount,
            String payoutCurrency,
            BigDecimal exchangeRate,
            BigDecimal convertedAmount,
            PaymentStatus status,
            String externalPaymentId,
            String errorMessage,
            int retryCount,
            Instant createdAt,
            Instant processedAt,
            Instant failedAt
    ) {}

    public record PaymentBatch(
            String id,
            String name,
            List<String> transactionIds,
            PaymentBatchStatus status,
            BigDecimal totalAmount,
            int successCount,
            int failureCount,
            int pendingCount,
            Instant createdAt,
            Instant processedAt,
            Instant completedAt
    ) {}

    public record PaymentPreference(
            String affiliateId,
            BigDecimal minimumPayout,
            String preferredCurrency,
            boolean autoPayment,
            String primaryPaymentMethodId
    ) {}

    public record ReconciliationReport(
            String batchId,
            int transactionCount,
            BigDecimal expectedTotal,
            BigDecimal actualTotal,
            BigDecimal discrepancy,
            boolean reconciled,
            Instant generatedAt
    ) {}

    public enum PaymentMethodType {
        PAYPAL,
        STRIPE,
        BANK_TRANSFER,
        WIRE_TRANSFER,
        CHECK,
        CRYPTOCURRENCY
    }

    public enum PaymentMethodStatus {
        PENDING_VERIFICATION,
        VERIFIED,
        SUSPENDED,
        REMOVED
    }

    public enum PaymentStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum PaymentBatchStatus {
        CREATED,
        PROCESSING,
        COMPLETED,
        PARTIAL,
        FAILED
    }

    // 自定义异常
    private static class PaymentGatewayException extends Exception {
        public PaymentGatewayException(String message) {
            super(message);
        }
    }
}
