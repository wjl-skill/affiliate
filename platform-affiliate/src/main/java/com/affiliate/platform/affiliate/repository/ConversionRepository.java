package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.Conversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 转化数据访问层
 */
@Repository
public interface ConversionRepository extends JpaRepository<Conversion, String> {

    /**
     * 根据转化ID查找
     */
    Optional<Conversion> findByConversionId(String conversionId);

    /**
     * 根据点击ID查找转化
     */
    Optional<Conversion> findByClickId(String clickId);

    /**
     * 根据渠道ID查找转化
     */
    List<Conversion> findByAffiliateIdOrderByCreatedAtDesc(String affiliateId);

    /**
     * 根据Offer ID查找转化
     */
    List<Conversion> findByOfferIdOrderByCreatedAtDesc(String offerId);

    /**
     * 根据状态查找转化
     */
    List<Conversion> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * 查找时间范围内的转化
     */
    @Query("SELECT c FROM Conversion c WHERE c.createdAt BETWEEN :from AND :to ORDER BY c.createdAt DESC")
    List<Conversion> findConversionsInTimeRange(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 查找渠道在指定时间范围内的转化
     */
    @Query("SELECT c FROM Conversion c WHERE c.affiliateId = :affiliateId AND c.createdAt BETWEEN :from AND :to ORDER BY c.createdAt DESC")
    List<Conversion> findConversionsByAffiliateInRange(
            @Param("affiliateId") String affiliateId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 统计渠道的转化数
     */
    @Query("SELECT COUNT(c) FROM Conversion c WHERE c.affiliateId = :affiliateId")
    long countByAffiliateId(@Param("affiliateId") String affiliateId);

    /**
     * 统计Offer的转化数
     */
    @Query("SELECT COUNT(c) FROM Conversion c WHERE c.offerId = :offerId")
    long countByOfferId(@Param("offerId") String offerId);

    /**
     * 根据外部交易ID查找转化
     */
    @Query("SELECT c FROM Conversion c WHERE c.offerId = :offerId AND c.txId = :txId")
    Optional<Conversion> findByOfferIdAndTxId(@Param("offerId") String offerId, @Param("txId") String txId);

    /**
     * 检查转化是否存在
     */
    boolean existsByConversionId(String conversionId);

    /**
     * 检查点击是否已转化
     */
    boolean existsByClickId(String clickId);
}
