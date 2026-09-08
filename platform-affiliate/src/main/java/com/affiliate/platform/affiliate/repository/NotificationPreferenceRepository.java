package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 通知偏好设置数据访问层
 */
@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, String> {

    /**
     * 查找用户的所有通知偏好
     */
    List<NotificationPreferenceEntity> findByAffiliateId(String affiliateId);

    /**
     * 查找用户对特定通知类型的偏好
     */
    Optional<NotificationPreferenceEntity> findByAffiliateIdAndNotificationType(
            String affiliateId,
            String notificationType
    );

    /**
     * 检查是否存在偏好设置
     */
    boolean existsByAffiliateIdAndNotificationType(String affiliateId, String notificationType);
}
