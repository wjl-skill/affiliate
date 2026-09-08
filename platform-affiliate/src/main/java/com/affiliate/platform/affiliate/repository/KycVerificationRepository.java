package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.KycVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * KYC身份验证数据访问层
 */
@Repository
public interface KycVerificationRepository extends JpaRepository<KycVerificationEntity, String> {

    /**
     * 根据渠道ID查找KYC验证记录
     */
    Optional<KycVerificationEntity> findByAffiliateId(String affiliateId);

    /**
     * 查找指定状态的KYC记录
     */
    List<KycVerificationEntity> findByStatusOrderByInitiatedAtAsc(String status);

    /**
     * 检查渠道是否已通过KYC验证
     */
    @Query("SELECT CASE WHEN COUNT(k) > 0 THEN true ELSE false END " +
           "FROM KycVerificationEntity k WHERE k.affiliateId = :affiliateId AND k.status = 'VERIFIED'")
    boolean isVerified(String affiliateId);

    /**
     * 统计指定状态的KYC记录数量
     */
    long countByStatus(String status);

    /**
     * 检查是否存在KYC记录
     */
    boolean existsByAffiliateId(String affiliateId);
}
