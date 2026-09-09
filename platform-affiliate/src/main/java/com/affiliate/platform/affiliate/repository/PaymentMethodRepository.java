package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.PaymentMethodEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/** MyBatis-Plus payment method store. */
@Mapper
public interface PaymentMethodRepository extends BaseMapper<PaymentMethodEntity> {
    default PaymentMethodEntity save(PaymentMethodEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default Optional<PaymentMethodEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default List<PaymentMethodEntity> findByAffiliateIdOrderByCreatedAtDesc(String affiliateId) {
        return selectList(new LambdaQueryWrapper<PaymentMethodEntity>().eq(PaymentMethodEntity::getAffiliateId, affiliateId)
                .orderByDesc(PaymentMethodEntity::getCreatedAt));
    }
    default Optional<PaymentMethodEntity> findByAffiliateIdAndIsPrimaryTrue(String affiliateId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<PaymentMethodEntity>().eq(PaymentMethodEntity::getAffiliateId, affiliateId)
                .eq(PaymentMethodEntity::getIsPrimary, true).last("LIMIT 1")));
    }
    default List<PaymentMethodEntity> findByAffiliateIdAndStatus(String affiliateId, String status) {
        return selectList(new LambdaQueryWrapper<PaymentMethodEntity>().eq(PaymentMethodEntity::getAffiliateId, affiliateId)
                .eq(PaymentMethodEntity::getStatus, status));
    }
    default List<PaymentMethodEntity> findVerifiedPaymentMethods(String affiliateId) {
        return selectList(new LambdaQueryWrapper<PaymentMethodEntity>().eq(PaymentMethodEntity::getAffiliateId, affiliateId)
                .eq(PaymentMethodEntity::getStatus, "VERIFIED").orderByDesc(PaymentMethodEntity::getIsPrimary)
                .orderByDesc(PaymentMethodEntity::getLastUsedAt));
    }
    default long countByAffiliateIdAndStatus(String affiliateId, String status) {
        return selectCount(new LambdaQueryWrapper<PaymentMethodEntity>().eq(PaymentMethodEntity::getAffiliateId, affiliateId)
                .eq(PaymentMethodEntity::getStatus, status));
    }
    default boolean existsByAffiliateIdAndIsPrimaryTrue(String affiliateId) {
        return selectCount(new LambdaQueryWrapper<PaymentMethodEntity>().eq(PaymentMethodEntity::getAffiliateId, affiliateId)
                .eq(PaymentMethodEntity::getIsPrimary, true)) > 0;
    }
}
