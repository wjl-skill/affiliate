package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.ReferralRelationshipEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 推荐关系数据访问层
 */
@Repository
public interface ReferralRelationshipRepository extends JpaRepository<ReferralRelationshipEntity, String> {

    /**
     * 根据被推荐人ID查找推荐关系
     */
    Optional<ReferralRelationshipEntity> findByRefereeId(String refereeId);

    /**
     * 查找推荐人的所有直接下线
     */
    List<ReferralRelationshipEntity> findByReferrerIdOrderByCreatedAtDesc(String referrerId);

    /**
     * 查找推荐人指定层级的下线
     */
    List<ReferralRelationshipEntity> findByReferrerIdAndTierOrderByCreatedAtDesc(
            String referrerId,
            Integer tier
    );

    /**
     * 查找推荐人指定状态的下线
     */
    List<ReferralRelationshipEntity> findByReferrerIdAndStatus(String referrerId, String status);

    /**
     * 根据推荐码查找关系
     */
    Optional<ReferralRelationshipEntity> findByReferralCode(String referralCode);

    /**
     * 统计推荐人的下线数量
     */
    long countByReferrerId(String referrerId);

    /**
     * 统计推荐人指定层级的下线数量
     */
    long countByReferrerIdAndTier(String referrerId, Integer tier);

    /**
     * 检查是否存在推荐关系
     */
    boolean existsByRefereeId(String refereeId);

    /**
     * 查找活跃的推荐关系
     */
    @Query("SELECT r FROM ReferralRelationshipEntity r WHERE r.status = 'ACTIVE' " +
           "ORDER BY r.totalCommissionEarned DESC")
    List<ReferralRelationshipEntity> findActiveRelationships();

    /**
     * 查找高价值推荐关系（按总佣金排序）
     */
    @Query("SELECT r FROM ReferralRelationshipEntity r WHERE r.referrerId = :referrerId " +
           "ORDER BY r.totalCommissionEarned DESC")
    List<ReferralRelationshipEntity> findTopEarningReferrals(@Param("referrerId") String referrerId);
}
