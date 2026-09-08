package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Offer数据访问层
 */
@Repository
public interface OfferRepository extends JpaRepository<Offer, String> {

    /**
     * 根据广告主ID查找Offer
     */
    List<Offer> findByAdvertiserIdOrderByCreatedAtDesc(String advertiserId);

    /**
     * 根据状态查找Offer
     */
    List<Offer> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * 根据类型查找Offer
     */
    List<Offer> findByOfferTypeOrderByCreatedAtDesc(String offerType);

    /**
     * 查找活跃的Offer
     */
    @Query("SELECT o FROM Offer o WHERE o.status = 'ACTIVE' ORDER BY o.createdAt DESC")
    List<Offer> findActiveOffers();

    /**
     * 根据分类查找Offer
     */
    List<Offer> findByCategoryOrderByCreatedAtDesc(String category);

    /**
     * 搜索Offer（按名称或描述）
     */
    @Query("SELECT o FROM Offer o WHERE LOWER(o.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(o.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Offer> searchOffers(@Param("keyword") String keyword);

    /**
     * 检查Offer是否存在
     */
    boolean existsById(String offerId);
}
