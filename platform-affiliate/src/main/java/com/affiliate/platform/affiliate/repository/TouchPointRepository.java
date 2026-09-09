package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.TouchPointEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** MyBatis-Plus touch point store. */
@Mapper
public interface TouchPointRepository extends BaseMapper<TouchPointEntity> {
    default TouchPointEntity save(TouchPointEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default Optional<TouchPointEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default List<TouchPointEntity> findTouchPointsInWindow(String userId, Instant windowStart, Instant windowEnd) {
        return selectList(new LambdaQueryWrapper<TouchPointEntity>().eq(TouchPointEntity::getUserId, userId)
                .ge(TouchPointEntity::getTimestamp, windowStart).lt(TouchPointEntity::getTimestamp, windowEnd)
                .orderByAsc(TouchPointEntity::getTimestamp));
    }
    default List<TouchPointEntity> findRecentTouchPoints(String userId, int limit) {
        return selectList(new LambdaQueryWrapper<TouchPointEntity>().eq(TouchPointEntity::getUserId, userId)
                .orderByDesc(TouchPointEntity::getTimestamp).last("LIMIT " + Math.max(0, limit)));
    }
    default List<Object[]> countTouchPointsByAffiliate(String userId) {
        return selectList(new LambdaQueryWrapper<TouchPointEntity>().eq(TouchPointEntity::getUserId, userId)).stream()
                .collect(Collectors.groupingBy(TouchPointEntity::getAffiliateId, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new Object[]{e.getKey(), e.getValue()}).toList();
    }
    default void deleteByTimestampBefore(Instant threshold) { delete(new LambdaQueryWrapper<TouchPointEntity>().lt(TouchPointEntity::getTimestamp, threshold)); }
    default long countByAffiliateId(String affiliateId) { return selectCount(new LambdaQueryWrapper<TouchPointEntity>().eq(TouchPointEntity::getAffiliateId, affiliateId)); }
}
