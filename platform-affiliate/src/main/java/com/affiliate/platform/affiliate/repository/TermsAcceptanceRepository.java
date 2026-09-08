package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.TermsAcceptanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 服务条款接受记录数据访问层
 */
@Repository
public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptanceEntity, String> {

    /**
     * 查找渠道的所有条款接受记录
     */
    List<TermsAcceptanceEntity> findByAffiliateIdOrderByAcceptedAtDesc(String affiliateId);

    /**
     * 查找渠道指定版本的条款接受记录
     */
    Optional<TermsAcceptanceEntity> findByAffiliateIdAndVersion(String affiliateId, String version);

    /**
     * 检查渠道是否接受指定版本
     */
    boolean existsByAffiliateIdAndVersion(String affiliateId, String version);

    /**
     * 查找所有未接受最新版本的渠道
     */
    @Query("SELECT DISTINCT t1.affiliateId FROM TermsAcceptanceEntity t1 " +
           "WHERE t1.affiliateId NOT IN " +
           "(SELECT t2.affiliateId FROM TermsAcceptanceEntity t2 WHERE t2.version = :latestVersion)")
    List<String> findAffiliatesNotAcceptedVersion(@Param("latestVersion") String latestVersion);
}
