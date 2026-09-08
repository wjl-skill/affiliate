package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 支付交易数据访问层
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, String> {

    /**
     * 查找渠道的所有交易
     */
    List<PaymentTransactionEntity> findByAffiliateIdOrderByCreatedAtDesc(String affiliateId);

    /**
     * 查找渠道指定状态的交易
     */
    List<PaymentTransactionEntity> findByAffiliateIdAndStatusOrderByCreatedAtDesc(
            String affiliateId,
            String status
    );

    /**
     * 查找时间范围内的交易
     */
    @Query("SELECT p FROM PaymentTransactionEntity p WHERE p.affiliateId = :affiliateId " +
           "AND p.createdAt >= :from AND p.createdAt < :to " +
           "ORDER BY p.createdAt DESC")
    List<PaymentTransactionEntity> findByAffiliateAndTimeRange(
            @Param("affiliateId") String affiliateId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 查找时间范围内指定状态的交易
     */
    @Query("SELECT p FROM PaymentTransactionEntity p WHERE p.affiliateId = :affiliateId " +
           "AND p.status = :status AND p.createdAt >= :from AND p.createdAt < :to " +
           "ORDER BY p.createdAt DESC")
    List<PaymentTransactionEntity> findByAffiliateStatusAndTimeRange(
            @Param("affiliateId") String affiliateId,
            @Param("status") String status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 根据发票ID查找交易
     */
    Optional<PaymentTransactionEntity> findByInvoiceId(String invoiceId);

    /**
     * 根据外部支付ID查找交易
     */
    Optional<PaymentTransactionEntity> findByExternalPaymentId(String externalPaymentId);

    /**
     * 查找失败的交易（可重试）
     */
    @Query("SELECT p FROM PaymentTransactionEntity p WHERE p.status = 'FAILED' " +
           "AND p.retryCount < :maxRetries ORDER BY p.createdAt DESC")
    List<PaymentTransactionEntity> findRetryableTransactions(@Param("maxRetries") int maxRetries);

    /**
     * 查找待处理的交易
     */
    List<PaymentTransactionEntity> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * 统计渠道的总支付金额
     */
    @Query("SELECT SUM(p.amount) FROM PaymentTransactionEntity p " +
           "WHERE p.affiliateId = :affiliateId AND p.status = 'COMPLETED'")
    BigDecimal sumCompletedPaymentsByAffiliate(@Param("affiliateId") String affiliateId);

    /**
     * 统计时间范围内的支付金额
     */
    @Query("SELECT SUM(p.amount) FROM PaymentTransactionEntity p " +
           "WHERE p.status = 'COMPLETED' AND p.createdAt >= :from AND p.createdAt < :to")
    BigDecimal sumCompletedPaymentsByTimeRange(
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 按状态统计交易数量
     */
    @Query("SELECT p.status, COUNT(p) FROM PaymentTransactionEntity p GROUP BY p.status")
    List<Object[]> countByStatus();

    /**
     * 按支付方式统计
     */
    @Query("SELECT p.paymentMethodId, COUNT(p), SUM(p.amount) FROM PaymentTransactionEntity p " +
           "WHERE p.status = 'COMPLETED' GROUP BY p.paymentMethodId")
    List<Object[]> statsByPaymentMethod();
}
