package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.PerformanceReportCacheEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** MyBatis-Plus performance report cache store. */
@Mapper
public interface PerformanceReportCacheRepository extends BaseMapper<PerformanceReportCacheEntity> {
    default PerformanceReportCacheEntity save(PerformanceReportCacheEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default Optional<PerformanceReportCacheEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default Optional<PerformanceReportCacheEntity> findByReportHash(String hash) { return Optional.ofNullable(selectOne(new LambdaQueryWrapper<PerformanceReportCacheEntity>().eq(PerformanceReportCacheEntity::getReportHash, hash).last("LIMIT 1"))); }
    default List<PerformanceReportCacheEntity> findByReportType(String type) { return selectList(new LambdaQueryWrapper<PerformanceReportCacheEntity>().eq(PerformanceReportCacheEntity::getReportType, type)); }
    default List<PerformanceReportCacheEntity> findByAffiliateId(String affiliateId) { return selectList(new LambdaQueryWrapper<PerformanceReportCacheEntity>().eq(PerformanceReportCacheEntity::getAffiliateId, affiliateId)); }
    default List<PerformanceReportCacheEntity> findByOfferId(String offerId) { return selectList(new LambdaQueryWrapper<PerformanceReportCacheEntity>().eq(PerformanceReportCacheEntity::getOfferId, offerId)); }
    default List<PerformanceReportCacheEntity> findByDateRange(LocalDate startDate, LocalDate endDate) { return selectList(new LambdaQueryWrapper<PerformanceReportCacheEntity>().ge(PerformanceReportCacheEntity::getStartDate, startDate).le(PerformanceReportCacheEntity::getEndDate, endDate)); }
    default int deleteExpiredReports(Instant now) { return delete(new LambdaQueryWrapper<PerformanceReportCacheEntity>().lt(PerformanceReportCacheEntity::getExpiresAt, now)); }
    default void deleteByAffiliateId(String affiliateId) { delete(new LambdaQueryWrapper<PerformanceReportCacheEntity>().eq(PerformanceReportCacheEntity::getAffiliateId, affiliateId)); }
    default void deleteByOfferId(String offerId) { delete(new LambdaQueryWrapper<PerformanceReportCacheEntity>().eq(PerformanceReportCacheEntity::getOfferId, offerId)); }
    default long countByReportType(String type) { return selectCount(new LambdaQueryWrapper<PerformanceReportCacheEntity>().eq(PerformanceReportCacheEntity::getReportType, type)); }
    default boolean existsValidReport(String hash, Instant now) { return selectCount(new LambdaQueryWrapper<PerformanceReportCacheEntity>().eq(PerformanceReportCacheEntity::getReportHash, hash).gt(PerformanceReportCacheEntity::getExpiresAt, now)) > 0; }
}
