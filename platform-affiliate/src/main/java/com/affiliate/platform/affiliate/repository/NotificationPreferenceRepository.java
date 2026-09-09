package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.NotificationPreferenceEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/** MyBatis-Plus notification preference store. */
@Mapper
public interface NotificationPreferenceRepository extends BaseMapper<NotificationPreferenceEntity> {
    default NotificationPreferenceEntity save(NotificationPreferenceEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default Optional<NotificationPreferenceEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default List<NotificationPreferenceEntity> findByAffiliateId(String affiliateId) {
        return selectList(new LambdaQueryWrapper<NotificationPreferenceEntity>().eq(NotificationPreferenceEntity::getAffiliateId, affiliateId));
    }
    default Optional<NotificationPreferenceEntity> findByAffiliateIdAndNotificationType(String affiliateId, String notificationType) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<NotificationPreferenceEntity>()
                .eq(NotificationPreferenceEntity::getAffiliateId, affiliateId)
                .eq(NotificationPreferenceEntity::getNotificationType, notificationType)));
    }
    default boolean existsByAffiliateIdAndNotificationType(String affiliateId, String notificationType) {
        return selectCount(new LambdaQueryWrapper<NotificationPreferenceEntity>()
                .eq(NotificationPreferenceEntity::getAffiliateId, affiliateId)
                .eq(NotificationPreferenceEntity::getNotificationType, notificationType)) > 0;
    }
}
