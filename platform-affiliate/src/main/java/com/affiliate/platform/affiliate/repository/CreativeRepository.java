package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.CreativeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 营销素材数据访问层
 */
@Repository
public interface CreativeRepository extends JpaRepository<CreativeEntity, String> {

    /**
     * 查找Offer的所有素材
     */
    List<CreativeEntity> findByOfferIdOrderByCreatedAtDesc(String offerId);

    /**
     * 查找Offer指定类型的素材
     */
    List<CreativeEntity> findByOfferIdAndTypeOrderByCreatedAtDesc(String offerId, String type);

    /**
     * 查找Offer指定状态的素材
     */
    List<CreativeEntity> findByOfferIdAndStatusOrderByCreatedAtDesc(String offerId, String status);

    /**
     * 查找Offer指定类型和状态的素材
     */
    List<CreativeEntity> findByOfferIdAndTypeAndStatusOrderByCreatedAtDesc(
            String offerId,
            String type,
            String status
    );

    /**
     * 查找待审核的素材
     */
    List<CreativeEntity> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * 搜索素材（按名称或描述）
     */
    @Query("SELECT c FROM CreativeEntity c WHERE " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY c.createdAt DESC")
    List<CreativeEntity> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 查找高性能素材（按点击量排序）
     */
    @Query("SELECT c FROM CreativeEntity c WHERE c.offerId = :offerId AND c.status = 'APPROVED' " +
           "ORDER BY c.clicks DESC")
    List<CreativeEntity> findTopPerformingByClicks(@Param("offerId") String offerId);

    /**
     * 查找高转化素材（按转化量排序）
     */
    @Query("SELECT c FROM CreativeEntity c WHERE c.offerId = :offerId AND c.status = 'APPROVED' " +
           "ORDER BY c.conversions DESC")
    List<CreativeEntity> findTopPerformingByConversions(@Param("offerId") String offerId);

    /**
     * 统计Offer的素材数量
     */
    long countByOfferId(String offerId);

    /**
     * 统计Offer指定状态的素材数量
     */
    long countByOfferIdAndStatus(String offerId, String status);

    /**
     * 增加素材点击数
     */
    @Modifying
    @Query("UPDATE CreativeEntity c SET c.clicks = c.clicks + 1 WHERE c.id = :creativeId")
    void incrementClicks(@Param("creativeId") String creativeId);

    /**
     * 增加素材转化数
     */
    @Modifying
    @Query("UPDATE CreativeEntity c SET c.conversions = c.conversions + 1 WHERE c.id = :creativeId")
    void incrementConversions(@Param("creativeId") String creativeId);

    /**
     * 批量更新状态
     */
    @Modifying
    @Query("UPDATE CreativeEntity c SET c.status = :newStatus WHERE c.id IN :creativeIds")
    void bulkUpdateStatus(@Param("creativeIds") List<String> creativeIds, @Param("newStatus") String newStatus);
}
