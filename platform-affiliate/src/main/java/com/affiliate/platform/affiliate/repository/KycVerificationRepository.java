package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.KycVerificationEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/** MyBatis-Plus KYC verification store. */
@Mapper
public interface KycVerificationRepository extends BaseMapper<KycVerificationEntity> {
    default KycVerificationEntity save(KycVerificationEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default Optional<KycVerificationEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default Optional<KycVerificationEntity> findByAffiliateId(String affiliateId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<KycVerificationEntity>().eq(KycVerificationEntity::getAffiliateId, affiliateId)
                .orderByDesc(KycVerificationEntity::getInitiatedAt).last("LIMIT 1")));
    }
    default List<KycVerificationEntity> findByStatusOrderByInitiatedAtAsc(String status) {
        return selectList(new LambdaQueryWrapper<KycVerificationEntity>().eq(KycVerificationEntity::getStatus, status)
                .orderByAsc(KycVerificationEntity::getInitiatedAt));
    }
    default boolean isVerified(String affiliateId) {
        return selectCount(new LambdaQueryWrapper<KycVerificationEntity>().eq(KycVerificationEntity::getAffiliateId, affiliateId)
                .eq(KycVerificationEntity::getStatus, "VERIFIED")) > 0;
    }
    default long countByStatus(String status) { return selectCount(new LambdaQueryWrapper<KycVerificationEntity>().eq(KycVerificationEntity::getStatus, status)); }
    default boolean existsByAffiliateId(String affiliateId) {
        return selectCount(new LambdaQueryWrapper<KycVerificationEntity>().eq(KycVerificationEntity::getAffiliateId, affiliateId)) > 0;
    }
}
