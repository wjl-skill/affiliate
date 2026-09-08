package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.ClickSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 点击会话数据访问层
 */
@Repository
public interface ClickSessionRepository extends JpaRepository<ClickSession, String> {

    /**
     * 根据点击ID查找会话
     */
    Optional<ClickSession> findByClickId(String clickId);

    /**
     * 根据渠道ID查找点击记录
     */
    List<ClickSession> findByAffiliateIdOrderByCreatedAtDesc(String affiliateId);

    /**
     * 根据Offer ID查找点击记录
     */
    List<ClickSession> findByOfferIdOrderByCreatedAtDesc(String offerId);

    /**
     * 根据IP地址查找点击记录
     */
    List<ClickSession> findByIpAddressOrderByCreatedAtDesc(String ipAddress);

    /**
     * 查找时间范围内的点击
     */
    @Query("SELECT c FROM ClickSession c WHERE c.createdAt BETWEEN :from AND :to ORDER BY c.createdAt DESC")
    List<ClickSession> findClicksInTimeRange(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 查找渠道在指定时间范围内的点击
     */
    @Query("SELECT c FROM ClickSession c WHERE c.affiliateId = :affiliateId AND c.createdAt BETWEEN :from AND :to ORDER BY c.createdAt DESC")
    List<ClickSession> findClicksByAffiliateInRange(
            @Param("affiliateId") String affiliateId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 统计渠道的点击数
     */
    @Query("SELECT COUNT(c) FROM ClickSession c WHERE c.affiliateId = :affiliateId")
    long countByAffiliateId(@Param("affiliateId") String affiliateId);

    /**
     * 统计Offer的点击数
     */
    @Query("SELECT COUNT(c) FROM ClickSession c WHERE c.offerId = :offerId")
    long countByOfferId(@Param("offerId") String offerId);

    /**
     * 删除过期点击记录
     */
    @Modifying
    @Query("DELETE FROM ClickSession c WHERE c.createdAt < :threshold")
    int deleteStaleClicks(@Param("threshold") Instant threshold);

    /**
     * 检查点击ID是否存在
     */
    boolean existsByClickId(String clickId);
}
