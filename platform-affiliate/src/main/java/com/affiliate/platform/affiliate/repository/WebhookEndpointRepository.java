package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.WebhookEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Webhook端点数据访问层
 */
@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpointEntity, String> {

    /**
     * 查找用户的所有Webhook端点
     */
    List<WebhookEndpointEntity> findByAffiliateId(String affiliateId);

    /**
     * 查找用户的活跃Webhook端点
     */
    List<WebhookEndpointEntity> findByAffiliateIdAndActive(String affiliateId, Boolean active);

    /**
     * 增加失败次数
     */
    @Modifying
    @Query("UPDATE WebhookEndpointEntity w SET w.failureCount = w.failureCount + 1, " +
           "w.lastFailedAt = :failedAt WHERE w.id = :webhookId")
    void incrementFailureCount(@Param("webhookId") String webhookId, @Param("failedAt") Instant failedAt);

    /**
     * 记录成功调用
     */
    @Modifying
    @Query("UPDATE WebhookEndpointEntity w SET w.failureCount = 0, " +
           "w.lastSuccessAt = :successAt WHERE w.id = :webhookId")
    void recordSuccess(@Param("webhookId") String webhookId, @Param("successAt") Instant successAt);

    /**
     * 禁用失败次数过多的端点
     */
    @Modifying
    @Query("UPDATE WebhookEndpointEntity w SET w.active = false " +
           "WHERE w.failureCount >= :threshold")
    int disableFailedEndpoints(@Param("threshold") int threshold);
}
