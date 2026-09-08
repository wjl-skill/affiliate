package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 通知数据访问层
 */
@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    /**
     * 查找用户的未读通知
     */
    List<NotificationEntity> findByRecipientIdAndIsReadOrderByCreatedAtDesc(
            String recipientId,
            Boolean isRead
    );

    /**
     * 查找用户的所有通知（分页）
     */
    List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    /**
     * 查找用户指定类型的通知
     */
    List<NotificationEntity> findByRecipientIdAndTypeOrderByCreatedAtDesc(
            String recipientId,
            String type
    );

    /**
     * 查找用户指定时间范围的通知
     */
    @Query("SELECT n FROM NotificationEntity n WHERE n.recipientId = :recipientId " +
           "AND n.createdAt >= :from AND n.createdAt <= :to " +
           "ORDER BY n.createdAt DESC")
    List<NotificationEntity> findByRecipientIdAndDateRange(
            @Param("recipientId") String recipientId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 查找用户指定类型和时间范围的通知
     */
    @Query("SELECT n FROM NotificationEntity n WHERE n.recipientId = :recipientId " +
           "AND n.type = :type " +
           "AND n.createdAt >= :from AND n.createdAt <= :to " +
           "ORDER BY n.createdAt DESC")
    List<NotificationEntity> findByRecipientIdAndTypeAndDateRange(
            @Param("recipientId") String recipientId,
            @Param("type") String type,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 统计用户的未读通知数量
     */
    long countByRecipientIdAndIsRead(String recipientId, Boolean isRead);

    /**
     * 批量标记已读
     */
    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true, n.readAt = :readAt " +
           "WHERE n.recipientId = :recipientId AND n.isRead = false")
    int markAllAsRead(@Param("recipientId") String recipientId, @Param("readAt") Instant readAt);

    /**
     * 删除旧通知（数据清理）
     */
    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.createdAt < :before")
    int deleteOldNotifications(@Param("before") Instant before);

    /**
     * 查找高优先级未读通知
     */
    @Query("SELECT n FROM NotificationEntity n WHERE n.recipientId = :recipientId " +
           "AND n.isRead = false AND n.priority IN ('HIGH', 'URGENT') " +
           "ORDER BY n.createdAt DESC")
    List<NotificationEntity> findUrgentUnreadNotifications(@Param("recipientId") String recipientId);
}
