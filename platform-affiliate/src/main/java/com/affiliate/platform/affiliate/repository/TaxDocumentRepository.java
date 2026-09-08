package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.TaxDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 税务文档数据访问层
 */
@Repository
public interface TaxDocumentRepository extends JpaRepository<TaxDocumentEntity, String> {

    /**
     * 查找渠道的所有税务文档
     */
    List<TaxDocumentEntity> findByAffiliateIdOrderByUploadedAtDesc(String affiliateId);

    /**
     * 查找渠道指定状态的税务文档
     */
    List<TaxDocumentEntity> findByAffiliateIdAndStatus(String affiliateId, String status);

    /**
     * 查找渠道有效的税务文档（已批准且未过期）
     */
    @Query("SELECT t FROM TaxDocumentEntity t WHERE t.affiliateId = :affiliateId " +
           "AND t.status = 'APPROVED' AND t.expiresAt > :now " +
           "ORDER BY t.expiresAt DESC")
    List<TaxDocumentEntity> findValidTaxDocuments(
            @Param("affiliateId") String affiliateId,
            @Param("now") Instant now
    );

    /**
     * 查找即将过期的税务文档（30天内过期）
     */
    @Query("SELECT t FROM TaxDocumentEntity t WHERE t.status = 'APPROVED' " +
           "AND t.expiresAt > :now AND t.expiresAt < :threshold " +
           "ORDER BY t.expiresAt ASC")
    List<TaxDocumentEntity> findExpiringDocuments(
            @Param("now") Instant now,
            @Param("threshold") Instant threshold
    );

    /**
     * 查找待审核的税务文档
     */
    List<TaxDocumentEntity> findByStatusOrderByUploadedAtAsc(String status);

    /**
     * 统计待审核文档数量
     */
    long countByStatus(String status);
}
