package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.CreativeEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/** MyBatis-Plus creative store. */
@Mapper
public interface CreativeRepository extends BaseMapper<CreativeEntity> {
    default CreativeEntity save(CreativeEntity entity) { if (entity == null) return null; if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity); else updateById(entity); return entity; }
    default Optional<CreativeEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default boolean existsById(String id) { return selectById(id) != null; }
    default List<CreativeEntity> findAll() { return selectList(null); }
    default List<CreativeEntity> findByOfferIdOrderByCreatedAtDesc(String id) { return selectList(new LambdaQueryWrapper<CreativeEntity>().eq(CreativeEntity::getOfferId, id).orderByDesc(CreativeEntity::getCreatedAt)); }
    default List<CreativeEntity> findByOfferIdAndTypeOrderByCreatedAtDesc(String offerId, String type) { return selectList(new LambdaQueryWrapper<CreativeEntity>().eq(CreativeEntity::getOfferId, offerId).eq(CreativeEntity::getType, type).orderByDesc(CreativeEntity::getCreatedAt)); }
    default List<CreativeEntity> findByOfferIdAndStatusOrderByCreatedAtDesc(String offerId, String status) { return selectList(new LambdaQueryWrapper<CreativeEntity>().eq(CreativeEntity::getOfferId, offerId).eq(CreativeEntity::getStatus, status).orderByDesc(CreativeEntity::getCreatedAt)); }
    default List<CreativeEntity> findByOfferIdAndTypeAndStatusOrderByCreatedAtDesc(String offerId, String type, String status) { return selectList(new LambdaQueryWrapper<CreativeEntity>().eq(CreativeEntity::getOfferId, offerId).eq(CreativeEntity::getType, type).eq(CreativeEntity::getStatus, status).orderByDesc(CreativeEntity::getCreatedAt)); }
    default List<CreativeEntity> findByStatusOrderByCreatedAtAsc(String status) { return selectList(new LambdaQueryWrapper<CreativeEntity>().eq(CreativeEntity::getStatus, status).orderByAsc(CreativeEntity::getCreatedAt)); }
    default List<CreativeEntity> searchByKeyword(String keyword) { String value = keyword == null ? "" : keyword; return selectList(new LambdaQueryWrapper<CreativeEntity>().and(q -> q.like(CreativeEntity::getName, value).or().like(CreativeEntity::getDescription, value)).orderByDesc(CreativeEntity::getCreatedAt)); }
    default List<CreativeEntity> findTopPerformingByClicks(String offerId) { return selectList(new LambdaQueryWrapper<CreativeEntity>().eq(CreativeEntity::getOfferId, offerId).eq(CreativeEntity::getStatus, "APPROVED").orderByDesc(CreativeEntity::getClicks)); }
    default List<CreativeEntity> findTopPerformingByConversions(String offerId) { return selectList(new LambdaQueryWrapper<CreativeEntity>().eq(CreativeEntity::getOfferId, offerId).eq(CreativeEntity::getStatus, "APPROVED").orderByDesc(CreativeEntity::getConversions)); }
    default long countByOfferId(String id) { return selectCount(new LambdaQueryWrapper<CreativeEntity>().eq(CreativeEntity::getOfferId, id)); }
    default long countByOfferIdAndStatus(String offerId, String status) { return selectCount(new LambdaQueryWrapper<CreativeEntity>().eq(CreativeEntity::getOfferId, offerId).eq(CreativeEntity::getStatus, status)); }
    default void incrementClicks(String id) { update(null, new LambdaUpdateWrapper<CreativeEntity>().eq(CreativeEntity::getId, id).setSql("clicks = COALESCE(clicks, 0) + 1")); }
    default void incrementConversions(String id) { update(null, new LambdaUpdateWrapper<CreativeEntity>().eq(CreativeEntity::getId, id).setSql("conversions = COALESCE(conversions, 0) + 1")); }
    default void bulkUpdateStatus(List<String> ids, String status) { if (ids != null && !ids.isEmpty()) update(null, new LambdaUpdateWrapper<CreativeEntity>().in(CreativeEntity::getId, ids).set(CreativeEntity::getStatus, status)); }
}
