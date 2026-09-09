package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.WebhookEndpointEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.List;

/** MyBatis-Plus webhook endpoint store. */
@Mapper
public interface WebhookEndpointRepository extends BaseMapper<WebhookEndpointEntity> {
    default WebhookEndpointEntity save(WebhookEndpointEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default List<WebhookEndpointEntity> findByAffiliateId(String affiliateId) {
        return selectList(new LambdaQueryWrapper<WebhookEndpointEntity>().eq(WebhookEndpointEntity::getAffiliateId, affiliateId));
    }
    default List<WebhookEndpointEntity> findByAffiliateIdAndActive(String affiliateId, Boolean active) {
        return selectList(new LambdaQueryWrapper<WebhookEndpointEntity>().eq(WebhookEndpointEntity::getAffiliateId, affiliateId)
                .eq(WebhookEndpointEntity::getActive, active));
    }
    default void incrementFailureCount(String webhookId, Instant failedAt) {
        WebhookEndpointEntity entity = selectById(webhookId);
        if (entity == null) return;
        update(null, new LambdaUpdateWrapper<WebhookEndpointEntity>().eq(WebhookEndpointEntity::getId, webhookId)
                .set(WebhookEndpointEntity::getFailureCount, (entity.getFailureCount() == null ? 0 : entity.getFailureCount()) + 1)
                .set(WebhookEndpointEntity::getLastFailedAt, failedAt));
    }
    default void recordSuccess(String webhookId, Instant successAt) {
        update(null, new LambdaUpdateWrapper<WebhookEndpointEntity>().eq(WebhookEndpointEntity::getId, webhookId)
                .set(WebhookEndpointEntity::getFailureCount, 0).set(WebhookEndpointEntity::getLastSuccessAt, successAt));
    }
    default int disableFailedEndpoints(int threshold) {
        return update(null, new LambdaUpdateWrapper<WebhookEndpointEntity>().ge(WebhookEndpointEntity::getFailureCount, threshold)
                .set(WebhookEndpointEntity::getActive, false));
    }
}
