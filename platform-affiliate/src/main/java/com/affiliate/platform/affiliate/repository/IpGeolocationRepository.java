package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.IpGeolocationCacheEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** MyBatis-Plus IP intelligence cache store. */
@Mapper
public interface IpGeolocationRepository extends BaseMapper<IpGeolocationCacheEntity> {
    default IpGeolocationCacheEntity save(IpGeolocationCacheEntity entity) { if (entity == null) return null; IpGeolocationCacheEntity existing = findByIpAddress(entity.getIpAddress()).orElse(null); if (existing == null) insert(entity); else { entity.setId(existing.getId()); updateById(entity); } return entity; }
    default Optional<IpGeolocationCacheEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default Optional<IpGeolocationCacheEntity> findByIpAddress(String ip) { return Optional.ofNullable(selectOne(new LambdaQueryWrapper<IpGeolocationCacheEntity>().eq(IpGeolocationCacheEntity::getIpAddress, ip).last("LIMIT 1"))); }
    default List<IpGeolocationCacheEntity> findSuspiciousIps() { return selectList(new LambdaQueryWrapper<IpGeolocationCacheEntity>().eq(IpGeolocationCacheEntity::getIsVpn, true).or().eq(IpGeolocationCacheEntity::getIsProxy, true).or().eq(IpGeolocationCacheEntity::getIsTor, true)); }
    default List<IpGeolocationCacheEntity> findByCountryCode(String code) { return selectList(new LambdaQueryWrapper<IpGeolocationCacheEntity>().eq(IpGeolocationCacheEntity::getCountryCode, code)); }
    default List<IpGeolocationCacheEntity> findHighRiskIps(int minScore) { return selectList(new LambdaQueryWrapper<IpGeolocationCacheEntity>().ge(IpGeolocationCacheEntity::getRiskScore, minScore).orderByDesc(IpGeolocationCacheEntity::getRiskScore)); }
    default List<IpGeolocationCacheEntity> findStaleRecords(Instant threshold) { return selectList(new LambdaQueryWrapper<IpGeolocationCacheEntity>().lt(IpGeolocationCacheEntity::getUpdatedAt, threshold)); }
    default void deleteByUpdatedAtBefore(Instant threshold) { delete(new LambdaQueryWrapper<IpGeolocationCacheEntity>().lt(IpGeolocationCacheEntity::getUpdatedAt, threshold)); }
    default int deleteStaleRecords(Instant threshold) { return delete(new LambdaQueryWrapper<IpGeolocationCacheEntity>().lt(IpGeolocationCacheEntity::getUpdatedAt, threshold)); }
    default List<Object[]> countByCountry() { return selectList(null).stream().collect(Collectors.groupingBy(IpGeolocationCacheEntity::getCountryCode, LinkedHashMap::new, Collectors.counting())).entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).map(e -> new Object[]{e.getKey(), e.getValue()}).toList(); }
    default List<Object[]> getCountryDistribution() { return countByCountry(); }
    default List<Object[]> countByIsp() { return selectList(new LambdaQueryWrapper<IpGeolocationCacheEntity>().isNotNull(IpGeolocationCacheEntity::getIsp)).stream().collect(Collectors.groupingBy(IpGeolocationCacheEntity::getIsp, LinkedHashMap::new, Collectors.counting())).entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).map(e -> new Object[]{e.getKey(), e.getValue()}).toList(); }
    default boolean existsByIpAddress(String ip) { return selectCount(new LambdaQueryWrapper<IpGeolocationCacheEntity>().eq(IpGeolocationCacheEntity::getIpAddress, ip)) > 0; }
}
