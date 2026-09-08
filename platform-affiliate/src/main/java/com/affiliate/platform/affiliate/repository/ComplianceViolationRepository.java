package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.ComplianceViolationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 合规违规记录数据访问层
 */
@Repository
public interface ComplianceViolationRepository extends JpaRepository<ComplianceViolationEntity, String> {

    /**
     * 查找渠道的所有违规记录
     */
    List<ComplianceViolationEntity> findByAffiliateIdOrderByDetectedAtDesc(String affiliateId);

    /**
     * 查找渠道指定状态的违规记录
     */
    List<ComplianceViolationEntity> findByAffiliateIdAndStatusOrderByDetectedAtDesc(
            String affiliateId,
            String status
    );

    /**
     * 查找渠道指定严重程度的违规记录
     */
    List<ComplianceViolationEntity> findByAffiliateIdAndSeverity(String affiliateId, String severity);

    /**
     * 查找时间范围内的违规记录
     */
    @Query("SELECT v FROM ComplianceViolationEntity v WHERE v.detectedAt >= :from " +
           "AND v.detectedAt < :to ORDER BY v.detectedAt DESC")
    List<ComplianceViolationEntity> findByTimeRange(
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 查找未处理的违规记录
     */
    @Query("SELECT v FROM ComplianceViolationEntity v WHERE v.status IN ('OPEN', 'UNDER_INVESTIGATION') " +
           "ORDER BY v.severity DESC, v.detectedAt ASC")
    List<ComplianceViolationEntity> findOpenViolations();

    /**
     * 统计渠道的违规数量（按状态）
     */
    long countByAffiliateIdAndStatus(String affiliateId, String status);

    /**
     * 统计渠道的严重违规数量
     */
    @Query("SELECT COUNT(v) FROM ComplianceViolationEntity v WHERE v.affiliateId = :affiliateId " +
           "AND v.severity IN ('HIGH', 'CRITICAL') AND v.status = 'OPEN'")
    long countCriticalViolations(@Param("affiliateId") String affiliateId);

    /**
     * 按违规类型统计
     */
    @Query("SELECT v.type, COUNT(v) FROM ComplianceViolationEntity v " +
           "WHERE v.status = 'OPEN' GROUP BY v.type ORDER BY COUNT(v) DESC")
    List<Object[]> countByType();
}
