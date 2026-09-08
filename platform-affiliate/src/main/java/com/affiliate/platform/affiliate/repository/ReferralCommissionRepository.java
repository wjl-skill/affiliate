package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.ReferralCommissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 推荐佣金流水数据访问层
 */
@Repository
public interface ReferralCommissionRepository extends JpaRepository<ReferralCommissionEntity, String> {

    /**
     * 查找推荐人的所有佣金记录
     */
    List<ReferralCommissionEntity> findByReferrerIdOrderByCreatedAtDesc(String referrerId);

    /**
     * 查找推荐人指定状态的佣金记录
     */
    List<ReferralCommissionEntity> findByReferrerIdAndStatusOrderByCreatedAtDesc(
            String referrerId,
            String status
    );

    /**
     * 查找时间范围内的佣金记录
     */
    @Query("SELECT r FROM ReferralCommissionEntity r WHERE r.referrerId = :referrerId " +
           "AND r.createdAt >= :from AND r.createdAt < :to " +
           "ORDER BY r.createdAt DESC")
    List<ReferralCommissionEntity> findByReferrerAndTimeRange(
            @Param("referrerId") String referrerId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 查找时间范围内指定状态的佣金记录
     */
    @Query("SELECT r FROM ReferralCommissionEntity r WHERE r.referrerId = :referrerId " +
           "AND r.status = :status AND r.createdAt >= :from AND r.createdAt < :to " +
           "ORDER BY r.createdAt DESC")
    List<ReferralCommissionEntity> findByReferrerStatusAndTimeRange(
            @Param("referrerId") String referrerId,
            @Param("status") String status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 根据转化ID查找佣金记录
     */
    List<ReferralCommissionEntity> findByConversionId(String conversionId);

    /**
     * 统计推荐人的总佣金（已批准）
     */
    @Query("SELECT SUM(r.commission) FROM ReferralCommissionEntity r " +
           "WHERE r.referrerId = :referrerId AND r.status = 'APPROVED'")
    BigDecimal sumApprovedCommissionByReferrer(@Param("referrerId") String referrerId);

    /**
     * 统计推荐人的待审核佣金
     */
    @Query("SELECT SUM(r.commission) FROM ReferralCommissionEntity r " +
           "WHERE r.referrerId = :referrerId AND r.status = 'PENDING'")
    BigDecimal sumPendingCommissionByReferrer(@Param("referrerId") String referrerId);

    /**
     * 统计推荐人的转化次数
     */
    long countByReferrerId(String referrerId);

    /**
     * 按状态统计佣金数量
     */
    @Query("SELECT r.status, COUNT(r) FROM ReferralCommissionEntity r GROUP BY r.status")
    List<Object[]> countByStatus();

    /**
     * 查找待处理的佣金记录
     */
    List<ReferralCommissionEntity> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * 推荐人排行榜（按总佣金）
     */
    @Query("SELECT r.referrerId, SUM(r.commission) as total FROM ReferralCommissionEntity r " +
           "WHERE r.status IN ('APPROVED', 'PAID') GROUP BY r.referrerId " +
           "ORDER BY total DESC")
    List<Object[]> findTopReferrersByEarnings();
}
