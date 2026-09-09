package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.AttributionResultEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** MyBatis-Plus attribution result store. */
@Mapper
public interface AttributionResultRepository extends BaseMapper<AttributionResultEntity> {
    default AttributionResultEntity save(AttributionResultEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default Optional<AttributionResultEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default Optional<AttributionResultEntity> findByConversionId(String conversionId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<AttributionResultEntity>().eq(AttributionResultEntity::getConversionId, conversionId).last("LIMIT 1")));
    }
    default List<AttributionResultEntity> findByUserIdOrderByConversionTimeDesc(String userId) {
        return selectList(new LambdaQueryWrapper<AttributionResultEntity>().eq(AttributionResultEntity::getUserId, userId)
                .orderByDesc(AttributionResultEntity::getConversionTime));
    }
    default List<AttributionResultEntity> findByTimeRange(Instant from, Instant to) {
        return selectList(new LambdaQueryWrapper<AttributionResultEntity>().ge(AttributionResultEntity::getConversionTime, from)
                .lt(AttributionResultEntity::getConversionTime, to).orderByDesc(AttributionResultEntity::getConversionTime));
    }
    default List<Object[]> countByAttributionModel() {
        return selectList(null).stream().collect(Collectors.groupingBy(AttributionResultEntity::getAttributionModel, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new Object[]{e.getKey(), e.getValue()}).toList();
    }
    default boolean existsByConversionId(String conversionId) {
        return selectCount(new LambdaQueryWrapper<AttributionResultEntity>().eq(AttributionResultEntity::getConversionId, conversionId)) > 0;
    }
}
