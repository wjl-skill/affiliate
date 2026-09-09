package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.ProductEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/** MyBatis-Plus product catalog store. */
@Mapper
public interface ProductRepository extends BaseMapper<ProductEntity> {
    default ProductEntity save(ProductEntity entity) {
        if (entity == null) return null;
        if (entity.getSku() == null || selectById(entity.getSku()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default Optional<ProductEntity> findById(String sku) { return Optional.ofNullable(selectById(sku)); }
    default List<ProductEntity> findByOfferIdOrderByNameAsc(String offerId) {
        return selectList(new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getOfferId, offerId).orderByAsc(ProductEntity::getName));
    }
    default Page<ProductEntity> findByOfferId(String offerId, Pageable pageable) {
        LambdaQueryWrapper<ProductEntity> query = new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getOfferId, offerId).orderByAsc(ProductEntity::getName);
        long total = selectCount(query);
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.max(1, pageable.getPageSize());
        List<ProductEntity> records = selectList(query.last("LIMIT " + size + " OFFSET " + ((long) page * size)));
        return new PageImpl<>(records, pageable, total);
    }
    default List<ProductEntity> findByOfferIdAndAvailability(String offerId, String availability) {
        return selectList(new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getOfferId, offerId).eq(ProductEntity::getAvailability, availability));
    }
    default List<ProductEntity> searchByKeyword(String offerId, String keyword) {
        String value = keyword == null ? "" : keyword;
        return selectList(new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getOfferId, offerId)
                .and(q -> q.like(ProductEntity::getName, value).or().like(ProductEntity::getDescription, value)));
    }
    default List<ProductEntity> findByCategoryIdOrderByNameAsc(String categoryId) {
        return selectList(new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getCategoryId, categoryId).orderByAsc(ProductEntity::getName));
    }
    default List<ProductEntity> findByBrandOrderByNameAsc(String brand) {
        return selectList(new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getBrand, brand).orderByAsc(ProductEntity::getName));
    }
    default long countByOfferId(String offerId) { return selectCount(new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getOfferId, offerId)); }
    default long countByOfferIdAndAvailability(String offerId, String availability) {
        return selectCount(new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getOfferId, offerId).eq(ProductEntity::getAvailability, availability));
    }
    default int bulkUpdateStock(List<String> skus, int quantity, String availability) {
        if (skus == null || skus.isEmpty()) return 0;
        return update(null, new LambdaUpdateWrapper<ProductEntity>().in(ProductEntity::getSku, skus)
                .set(ProductEntity::getStockQuantity, quantity).set(ProductEntity::getAvailability, availability));
    }
    default void deleteByOfferId(String offerId) { delete(new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getOfferId, offerId)); }
}
