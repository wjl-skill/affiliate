package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.ReferralRelationshipEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/** MyBatis-Plus referral relationship store. */
@Mapper
public interface ReferralRelationshipRepository extends BaseMapper<ReferralRelationshipEntity> {
    default ReferralRelationshipEntity save(ReferralRelationshipEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity); else updateById(entity);
        return entity;
    }
    default Optional<ReferralRelationshipEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default Optional<ReferralRelationshipEntity> findByRefereeId(String id) { return Optional.ofNullable(selectOne(new LambdaQueryWrapper<ReferralRelationshipEntity>().eq(ReferralRelationshipEntity::getRefereeId, id).last("LIMIT 1"))); }
    default List<ReferralRelationshipEntity> findByReferrerIdOrderByCreatedAtDesc(String id) { return selectList(new LambdaQueryWrapper<ReferralRelationshipEntity>().eq(ReferralRelationshipEntity::getReferrerId, id).orderByDesc(ReferralRelationshipEntity::getCreatedAt)); }
    default List<ReferralRelationshipEntity> findByReferrerIdAndTierOrderByCreatedAtDesc(String id, Integer tier) { return selectList(new LambdaQueryWrapper<ReferralRelationshipEntity>().eq(ReferralRelationshipEntity::getReferrerId, id).eq(ReferralRelationshipEntity::getTier, tier).orderByDesc(ReferralRelationshipEntity::getCreatedAt)); }
    default List<ReferralRelationshipEntity> findByReferrerIdAndStatus(String id, String status) { return selectList(new LambdaQueryWrapper<ReferralRelationshipEntity>().eq(ReferralRelationshipEntity::getReferrerId, id).eq(ReferralRelationshipEntity::getStatus, status)); }
    default Optional<ReferralRelationshipEntity> findByReferralCode(String code) { return Optional.ofNullable(selectOne(new LambdaQueryWrapper<ReferralRelationshipEntity>().eq(ReferralRelationshipEntity::getReferralCode, code).last("LIMIT 1"))); }
    default long countByReferrerId(String id) { return selectCount(new LambdaQueryWrapper<ReferralRelationshipEntity>().eq(ReferralRelationshipEntity::getReferrerId, id)); }
    default long countByReferrerIdAndTier(String id, Integer tier) { return selectCount(new LambdaQueryWrapper<ReferralRelationshipEntity>().eq(ReferralRelationshipEntity::getReferrerId, id).eq(ReferralRelationshipEntity::getTier, tier)); }
    default boolean existsByRefereeId(String id) { return selectCount(new LambdaQueryWrapper<ReferralRelationshipEntity>().eq(ReferralRelationshipEntity::getRefereeId, id)) > 0; }
    default List<ReferralRelationshipEntity> findActiveRelationships() { return selectList(new LambdaQueryWrapper<ReferralRelationshipEntity>().eq(ReferralRelationshipEntity::getStatus, "ACTIVE").orderByDesc(ReferralRelationshipEntity::getTotalCommissionEarned)); }
    default List<ReferralRelationshipEntity> findTopEarningReferrals(String id) { return selectList(new LambdaQueryWrapper<ReferralRelationshipEntity>().eq(ReferralRelationshipEntity::getReferrerId, id).orderByDesc(ReferralRelationshipEntity::getTotalCommissionEarned)); }
}
