package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.entity.OfferTierPayoutEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Offer阶梯出价数据访问层
 */
@Repository
public interface OfferTierPayoutRepository extends JpaRepository<OfferTierPayoutEntity, String> {

    /**
     * 根据Offer ID查找所有阶梯出价规则
     */
    List<OfferTierPayoutEntity> findByOfferIdOrderByCreatedAtDesc(String offerId);

    /**
     * 根据渠道ID查找专属出价
     */
    List<OfferTierPayoutEntity> findByAffiliateIdOrderByCreatedAtDesc(String affiliateId);

    /**
     * 根据目标等级查找出价规则
     */
    List<OfferTierPayoutEntity> findByTargetTierOrderByCreatedAtDesc(String targetTier);

    /**
     * 查找特定Offer和渠道的出价规则
     */
    @Query("SELECT t FROM OfferTierPayoutEntity t WHERE t.offerId = :offerId AND t.affiliateId = :affiliateId")
    Optional<OfferTierPayoutEntity> findByOfferIdAndAffiliateId(
            @Param("offerId") String offerId,
            @Param("affiliateId") String affiliateId
    );

    /**
     * 查找特定Offer和等级的出价规则
     */
    @Query("SELECT t FROM OfferTierPayoutEntity t WHERE t.offerId = :offerId AND t.targetTier = :targetTier")
    Optional<OfferTierPayoutEntity> findByOfferIdAndTargetTier(
            @Param("offerId") String offerId,
            @Param("targetTier") String targetTier
    );

    /**
     * 删除特定Offer的所有出价规则
     */
    void deleteByOfferId(String offerId);

    /**
     * 检查Offer是否有自定义出价规则
     */
    boolean existsByOfferId(String offerId);
}
