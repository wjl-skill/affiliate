package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.OfferApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Offer申请数据访问层
 */
@Repository
public interface OfferApplicationRepository extends JpaRepository<OfferApplicationEntity, String> {

    /**
     * 查找渠道的所有申请
     */
    List<OfferApplicationEntity> findByAffiliateIdOrderByCreatedAtDesc(String affiliateId);

    /**
     * 查找Offer的所有申请
     */
    List<OfferApplicationEntity> findByOfferIdOrderByCreatedAtDesc(String offerId);

    /**
     * 查找渠道对特定Offer的申请
     */
    Optional<OfferApplicationEntity> findByOfferIdAndAffiliateId(String offerId, String affiliateId);

    /**
     * 查找待审批的申请
     */
    List<OfferApplicationEntity> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * 查找渠道已批准的申请
     */
    @Query("SELECT a FROM OfferApplicationEntity a WHERE a.affiliateId = :affiliateId " +
           "AND a.status = 'APPROVED'")
    List<OfferApplicationEntity> findApprovedApplicationsByAffiliate(@Param("affiliateId") String affiliateId);

    /**
     * 检查渠道是否有访问Offer的权限
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
           "FROM OfferApplicationEntity a WHERE a.offerId = :offerId " +
           "AND a.affiliateId = :affiliateId AND a.status = 'APPROVED'")
    boolean hasAccess(@Param("offerId") String offerId, @Param("affiliateId") String affiliateId);

    /**
     * 统计待审批申请数量
     */
    long countByStatus(String status);

    /**
     * 统计Offer的申请数量
     */
    long countByOfferId(String offerId);

    /**
     * 检查是否已存在申请
     */
    boolean existsByOfferIdAndAffiliateId(String offerId, String affiliateId);
}
