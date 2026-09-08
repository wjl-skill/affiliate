package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * API Key 数据访问层
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {

    /**
     * 根据密钥查找
     */
    Optional<ApiKeyEntity> findBySecretKey(String secretKey);

    /**
     * 查找渠道的所有密钥
     */
    List<ApiKeyEntity> findByAffiliateIdOrderByCreatedAtDesc(String affiliateId);

    /**
     * 查找渠道的指定状态密钥
     */
    List<ApiKeyEntity> findByAffiliateIdAndStatus(String affiliateId, String status);

    /**
     * 查找即将过期的密钥
     */
    @Query("SELECT k FROM ApiKeyEntity k WHERE k.status = 'ACTIVE' AND k.expiresAt IS NOT NULL AND k.expiresAt < :threshold")
    List<ApiKeyEntity> findExpiringKeys(@Param("threshold") Instant threshold);

    /**
     * 查找已过期但未撤销的密钥
     */
    @Query("SELECT k FROM ApiKeyEntity k WHERE k.status IN ('ACTIVE', 'DEPRECATED') AND k.expiresAt IS NOT NULL AND k.expiresAt < :now")
    List<ApiKeyEntity> findExpiredKeys(@Param("now") Instant now);

    /**
     * 统计渠道的活跃密钥数量
     */
    long countByAffiliateIdAndStatus(String affiliateId, String status);

    /**
     * 检查密钥是否存在
     */
    boolean existsBySecretKey(String secretKey);
}
