package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.OfferApplicationEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/** MyBatis-Plus offer application store. */
@Mapper
public interface OfferApplicationRepository extends BaseMapper<OfferApplicationEntity> {
    default OfferApplicationEntity save(OfferApplicationEntity entity) { if (entity == null) return null; if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity); else updateById(entity); return entity; }
    default Optional<OfferApplicationEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default List<OfferApplicationEntity> findByAffiliateIdOrderByCreatedAtDesc(String id) { return selectList(new LambdaQueryWrapper<OfferApplicationEntity>().eq(OfferApplicationEntity::getAffiliateId, id).orderByDesc(OfferApplicationEntity::getCreatedAt)); }
    default List<OfferApplicationEntity> findByOfferIdOrderByCreatedAtDesc(String id) { return selectList(new LambdaQueryWrapper<OfferApplicationEntity>().eq(OfferApplicationEntity::getOfferId, id).orderByDesc(OfferApplicationEntity::getCreatedAt)); }
    default Optional<OfferApplicationEntity> findByOfferIdAndAffiliateId(String offerId, String affiliateId) { return Optional.ofNullable(selectOne(new LambdaQueryWrapper<OfferApplicationEntity>().eq(OfferApplicationEntity::getOfferId, offerId).eq(OfferApplicationEntity::getAffiliateId, affiliateId).last("LIMIT 1"))); }
    default List<OfferApplicationEntity> findByStatusOrderByCreatedAtAsc(String status) { return selectList(new LambdaQueryWrapper<OfferApplicationEntity>().eq(OfferApplicationEntity::getStatus, status).orderByAsc(OfferApplicationEntity::getCreatedAt)); }
    default List<OfferApplicationEntity> findApprovedApplicationsByAffiliate(String id) { return selectList(new LambdaQueryWrapper<OfferApplicationEntity>().eq(OfferApplicationEntity::getAffiliateId, id).eq(OfferApplicationEntity::getStatus, "APPROVED")); }
    default boolean hasAccess(String offerId, String affiliateId) { return selectCount(new LambdaQueryWrapper<OfferApplicationEntity>().eq(OfferApplicationEntity::getOfferId, offerId).eq(OfferApplicationEntity::getAffiliateId, affiliateId).eq(OfferApplicationEntity::getStatus, "APPROVED")) > 0; }
    default long countByStatus(String status) { return selectCount(new LambdaQueryWrapper<OfferApplicationEntity>().eq(OfferApplicationEntity::getStatus, status)); }
    default long countByOfferId(String id) { return selectCount(new LambdaQueryWrapper<OfferApplicationEntity>().eq(OfferApplicationEntity::getOfferId, id)); }
    default boolean existsByOfferIdAndAffiliateId(String offerId, String affiliateId) { return selectCount(new LambdaQueryWrapper<OfferApplicationEntity>().eq(OfferApplicationEntity::getOfferId, offerId).eq(OfferApplicationEntity::getAffiliateId, affiliateId)) > 0; }
}
