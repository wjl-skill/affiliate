package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.ComplianceViolationEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.List;

/** MyBatis-Plus compliance violation store. */
@Mapper
public interface ComplianceViolationRepository extends BaseMapper<ComplianceViolationEntity> {
    default ComplianceViolationEntity save(ComplianceViolationEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default java.util.Optional<ComplianceViolationEntity> findById(String id) { return java.util.Optional.ofNullable(selectById(id)); }
    default List<ComplianceViolationEntity> findByAffiliateIdOrderByDetectedAtDesc(String affiliateId) {
        return selectList(new LambdaQueryWrapper<ComplianceViolationEntity>().eq(ComplianceViolationEntity::getAffiliateId, affiliateId)
                .orderByDesc(ComplianceViolationEntity::getDetectedAt));
    }
    default List<ComplianceViolationEntity> findByAffiliateIdAndStatusOrderByDetectedAtDesc(String affiliateId, String status) {
        return selectList(new LambdaQueryWrapper<ComplianceViolationEntity>().eq(ComplianceViolationEntity::getAffiliateId, affiliateId)
                .eq(ComplianceViolationEntity::getStatus, status).orderByDesc(ComplianceViolationEntity::getDetectedAt));
    }
    default List<ComplianceViolationEntity> findByAffiliateIdAndSeverity(String affiliateId, String severity) {
        return selectList(new LambdaQueryWrapper<ComplianceViolationEntity>().eq(ComplianceViolationEntity::getAffiliateId, affiliateId)
                .eq(ComplianceViolationEntity::getSeverity, severity));
    }
    default List<ComplianceViolationEntity> findByTimeRange(Instant from, Instant to) {
        return selectList(new LambdaQueryWrapper<ComplianceViolationEntity>().ge(ComplianceViolationEntity::getDetectedAt, from)
                .lt(ComplianceViolationEntity::getDetectedAt, to).orderByDesc(ComplianceViolationEntity::getDetectedAt));
    }
    default List<ComplianceViolationEntity> findOpenViolations() {
        return selectList(new LambdaQueryWrapper<ComplianceViolationEntity>().in(ComplianceViolationEntity::getStatus, "OPEN", "UNDER_INVESTIGATION")
                .orderByDesc(ComplianceViolationEntity::getSeverity).orderByAsc(ComplianceViolationEntity::getDetectedAt));
    }
    default long countByAffiliateIdAndStatus(String affiliateId, String status) {
        return selectCount(new LambdaQueryWrapper<ComplianceViolationEntity>().eq(ComplianceViolationEntity::getAffiliateId, affiliateId)
                .eq(ComplianceViolationEntity::getStatus, status));
    }
    default long countCriticalViolations(String affiliateId) {
        return selectCount(new LambdaQueryWrapper<ComplianceViolationEntity>().eq(ComplianceViolationEntity::getAffiliateId, affiliateId)
                .in(ComplianceViolationEntity::getSeverity, "HIGH", "CRITICAL").eq(ComplianceViolationEntity::getStatus, "OPEN"));
    }
    default List<Object[]> countByType() {
        return selectList(new LambdaQueryWrapper<ComplianceViolationEntity>().eq(ComplianceViolationEntity::getStatus, "OPEN"))
                .stream().collect(java.util.stream.Collectors.groupingBy(ComplianceViolationEntity::getType,
                        java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()))
                .entrySet().stream().map(e -> new Object[]{e.getKey(), e.getValue()}).toList();
    }
}
