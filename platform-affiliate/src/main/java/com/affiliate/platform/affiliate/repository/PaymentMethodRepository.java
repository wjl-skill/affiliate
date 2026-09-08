package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.PaymentMethodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 支付方式数据访问层
 */
@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity, String> {

    /**
     * 查找渠道的所有支付方式
     */
    List<PaymentMethodEntity> findByAffiliateIdOrderByCreatedAtDesc(String affiliateId);

    /**
     * 查找渠道的主支付方式
     */
    Optional<PaymentMethodEntity> findByAffiliateIdAndIsPrimaryTrue(String affiliateId);

    /**
     * 查找渠道指定状态的支付方式
     */
    List<PaymentMethodEntity> findByAffiliateIdAndStatus(String affiliateId, String status);

    /**
     * 查找渠道已验证的支付方式
     */
    @Query("SELECT p FROM PaymentMethodEntity p WHERE p.affiliateId = :affiliateId " +
           "AND p.status = 'VERIFIED' ORDER BY p.isPrimary DESC, p.lastUsedAt DESC")
    List<PaymentMethodEntity> findVerifiedPaymentMethods(@Param("affiliateId") String affiliateId);

    /**
     * 统计渠道的支付方式数量
     */
    long countByAffiliateIdAndStatus(String affiliateId, String status);

    /**
     * 检查是否存在主支付方式
     */
    boolean existsByAffiliateIdAndIsPrimaryTrue(String affiliateId);
}
