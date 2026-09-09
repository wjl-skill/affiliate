package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.TaxDocumentEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** MyBatis-Plus tax document store. */
@Mapper
public interface TaxDocumentRepository extends BaseMapper<TaxDocumentEntity> {
    default Optional<TaxDocumentEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default TaxDocumentEntity save(TaxDocumentEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default List<TaxDocumentEntity> findByAffiliateIdOrderByUploadedAtDesc(String affiliateId) {
        return selectList(new LambdaQueryWrapper<TaxDocumentEntity>().eq(TaxDocumentEntity::getAffiliateId, affiliateId)
                .orderByDesc(TaxDocumentEntity::getUploadedAt));
    }
    default List<TaxDocumentEntity> findByAffiliateIdAndStatus(String affiliateId, String status) {
        return selectList(new LambdaQueryWrapper<TaxDocumentEntity>().eq(TaxDocumentEntity::getAffiliateId, affiliateId)
                .eq(TaxDocumentEntity::getStatus, status));
    }
    default List<TaxDocumentEntity> findValidTaxDocuments(String affiliateId, Instant now) {
        return selectList(new LambdaQueryWrapper<TaxDocumentEntity>().eq(TaxDocumentEntity::getAffiliateId, affiliateId)
                .eq(TaxDocumentEntity::getStatus, "APPROVED").gt(TaxDocumentEntity::getExpiresAt, now)
                .orderByDesc(TaxDocumentEntity::getExpiresAt));
    }
    default List<TaxDocumentEntity> findExpiringDocuments(Instant now, Instant threshold) {
        return selectList(new LambdaQueryWrapper<TaxDocumentEntity>().eq(TaxDocumentEntity::getStatus, "APPROVED")
                .gt(TaxDocumentEntity::getExpiresAt, now).lt(TaxDocumentEntity::getExpiresAt, threshold)
                .orderByAsc(TaxDocumentEntity::getExpiresAt));
    }
    default List<TaxDocumentEntity> findByStatusOrderByUploadedAtAsc(String status) {
        return selectList(new LambdaQueryWrapper<TaxDocumentEntity>().eq(TaxDocumentEntity::getStatus, status)
                .orderByAsc(TaxDocumentEntity::getUploadedAt));
    }
    default long countByStatus(String status) { return selectCount(new LambdaQueryWrapper<TaxDocumentEntity>().eq(TaxDocumentEntity::getStatus, status)); }
}
