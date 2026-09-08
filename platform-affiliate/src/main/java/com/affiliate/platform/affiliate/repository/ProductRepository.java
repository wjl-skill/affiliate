package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 商品数据访问层
 */
@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, String> {

    /**
     * 查找 Offer 的所有商品
     */
    List<ProductEntity> findByOfferIdOrderByNameAsc(String offerId);

    /**
     * 分页查找 Offer 的商品
     */
    Page<ProductEntity> findByOfferId(String offerId, Pageable pageable);

    /**
     * 查找指定状态的商品
     */
    List<ProductEntity> findByOfferIdAndAvailability(String offerId, String availability);

    /**
     * 搜索商品（关键词）
     */
    @Query("SELECT p FROM ProductEntity p WHERE p.offerId = :offerId " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<ProductEntity> searchByKeyword(@Param("offerId") String offerId,
                                       @Param("keyword") String keyword);

    /**
     * 按分类查找商品
     */
    List<ProductEntity> findByCategoryIdOrderByNameAsc(String categoryId);

    /**
     * 按品牌查找商品
     */
    List<ProductEntity> findByBrandOrderByNameAsc(String brand);

    /**
     * 统计 Offer 的商品数量
     */
    long countByOfferId(String offerId);

    /**
     * 统计缺货商品
     */
    long countByOfferIdAndAvailability(String offerId, String availability);

    /**
     * 批量更新库存
     */
    @Modifying
    @Query("UPDATE ProductEntity p SET p.stockQuantity = :quantity, p.availability = :availability WHERE p.sku IN :skus")
    int bulkUpdateStock(@Param("skus") List<String> skus,
                        @Param("quantity") int quantity,
                        @Param("availability") String availability);

    /**
     * 删除 Offer 的所有商品
     */
    @Modifying
    void deleteByOfferId(String offerId);
}
