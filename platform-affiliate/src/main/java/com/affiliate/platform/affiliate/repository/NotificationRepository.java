package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.NotificationEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** MyBatis-Plus notification store. */
@Mapper
public interface NotificationRepository extends BaseMapper<NotificationEntity> {
    default NotificationEntity save(NotificationEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default Optional<NotificationEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default List<NotificationEntity> findByRecipientIdAndIsReadOrderByCreatedAtDesc(String recipientId, Boolean isRead) {
        return selectList(new LambdaQueryWrapper<NotificationEntity>().eq(NotificationEntity::getRecipientId, recipientId)
                .eq(NotificationEntity::getIsRead, isRead).orderByDesc(NotificationEntity::getCreatedAt));
    }
    default List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(String recipientId) {
        return selectList(new LambdaQueryWrapper<NotificationEntity>().eq(NotificationEntity::getRecipientId, recipientId)
                .orderByDesc(NotificationEntity::getCreatedAt));
    }
    default List<NotificationEntity> findByRecipientIdAndTypeOrderByCreatedAtDesc(String recipientId, String type) {
        return selectList(new LambdaQueryWrapper<NotificationEntity>().eq(NotificationEntity::getRecipientId, recipientId)
                .eq(NotificationEntity::getType, type).orderByDesc(NotificationEntity::getCreatedAt));
    }
    default List<NotificationEntity> findByRecipientIdAndDateRange(String recipientId, Instant from, Instant to) {
        return selectList(new LambdaQueryWrapper<NotificationEntity>().eq(NotificationEntity::getRecipientId, recipientId)
                .ge(NotificationEntity::getCreatedAt, from).le(NotificationEntity::getCreatedAt, to)
                .orderByDesc(NotificationEntity::getCreatedAt));
    }
    default List<NotificationEntity> findByRecipientIdAndTypeAndDateRange(String recipientId, String type, Instant from, Instant to) {
        return selectList(new LambdaQueryWrapper<NotificationEntity>().eq(NotificationEntity::getRecipientId, recipientId)
                .eq(NotificationEntity::getType, type).ge(NotificationEntity::getCreatedAt, from)
                .le(NotificationEntity::getCreatedAt, to).orderByDesc(NotificationEntity::getCreatedAt));
    }
    default long countByRecipientIdAndIsRead(String recipientId, Boolean isRead) {
        return selectCount(new LambdaQueryWrapper<NotificationEntity>().eq(NotificationEntity::getRecipientId, recipientId)
                .eq(NotificationEntity::getIsRead, isRead));
    }
    default int markAllAsRead(String recipientId, Instant readAt) {
        return update(null, new LambdaUpdateWrapper<NotificationEntity>().eq(NotificationEntity::getRecipientId, recipientId)
                .eq(NotificationEntity::getIsRead, false).set(NotificationEntity::getIsRead, true)
                .set(NotificationEntity::getReadAt, readAt));
    }
    default int deleteOldNotifications(Instant before) {
        return delete(new LambdaQueryWrapper<NotificationEntity>().lt(NotificationEntity::getCreatedAt, before));
    }
    default List<NotificationEntity> findUrgentUnreadNotifications(String recipientId) {
        return selectList(new LambdaQueryWrapper<NotificationEntity>().eq(NotificationEntity::getRecipientId, recipientId)
                .eq(NotificationEntity::getIsRead, false).in(NotificationEntity::getPriority, "HIGH", "URGENT")
                .orderByDesc(NotificationEntity::getCreatedAt));
    }
}
