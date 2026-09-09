package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.ReferralCommissionEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** MyBatis-Plus referral commission store. */
@Mapper
public interface ReferralCommissionRepository extends BaseMapper<ReferralCommissionEntity> {
    default ReferralCommissionEntity save(ReferralCommissionEntity entity) { if (entity == null) return null; if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity); else updateById(entity); return entity; }
    default Optional<ReferralCommissionEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default List<ReferralCommissionEntity> findByReferrerIdOrderByCreatedAtDesc(String id) { return selectList(new LambdaQueryWrapper<ReferralCommissionEntity>().eq(ReferralCommissionEntity::getReferrerId, id).orderByDesc(ReferralCommissionEntity::getCreatedAt)); }
    default List<ReferralCommissionEntity> findByReferrerIdAndStatusOrderByCreatedAtDesc(String id, String status) { return selectList(new LambdaQueryWrapper<ReferralCommissionEntity>().eq(ReferralCommissionEntity::getReferrerId, id).eq(ReferralCommissionEntity::getStatus, status).orderByDesc(ReferralCommissionEntity::getCreatedAt)); }
    default List<ReferralCommissionEntity> findByReferrerAndTimeRange(String id, Instant from, Instant to) { return selectList(new LambdaQueryWrapper<ReferralCommissionEntity>().eq(ReferralCommissionEntity::getReferrerId, id).ge(ReferralCommissionEntity::getCreatedAt, from).lt(ReferralCommissionEntity::getCreatedAt, to).orderByDesc(ReferralCommissionEntity::getCreatedAt)); }
    default List<ReferralCommissionEntity> findByReferrerStatusAndTimeRange(String id, String status, Instant from, Instant to) { return selectList(new LambdaQueryWrapper<ReferralCommissionEntity>().eq(ReferralCommissionEntity::getReferrerId, id).eq(ReferralCommissionEntity::getStatus, status).ge(ReferralCommissionEntity::getCreatedAt, from).lt(ReferralCommissionEntity::getCreatedAt, to).orderByDesc(ReferralCommissionEntity::getCreatedAt)); }
    default List<ReferralCommissionEntity> findByConversionId(String id) { return selectList(new LambdaQueryWrapper<ReferralCommissionEntity>().eq(ReferralCommissionEntity::getConversionId, id)); }
    default BigDecimal sumApprovedCommissionByReferrer(String id) { return sum(selectList(new LambdaQueryWrapper<ReferralCommissionEntity>().eq(ReferralCommissionEntity::getReferrerId, id).eq(ReferralCommissionEntity::getStatus, "APPROVED"))); }
    default BigDecimal sumPendingCommissionByReferrer(String id) { return sum(selectList(new LambdaQueryWrapper<ReferralCommissionEntity>().eq(ReferralCommissionEntity::getReferrerId, id).eq(ReferralCommissionEntity::getStatus, "PENDING"))); }
    default long countByReferrerId(String id) { return selectCount(new LambdaQueryWrapper<ReferralCommissionEntity>().eq(ReferralCommissionEntity::getReferrerId, id)); }
    default List<Object[]> countByStatus() { return selectList(null).stream().collect(Collectors.groupingBy(ReferralCommissionEntity::getStatus, LinkedHashMap::new, Collectors.counting())).entrySet().stream().map(e -> new Object[]{e.getKey(), e.getValue()}).toList(); }
    default List<ReferralCommissionEntity> findByStatusOrderByCreatedAtAsc(String status) { return selectList(new LambdaQueryWrapper<ReferralCommissionEntity>().eq(ReferralCommissionEntity::getStatus, status).orderByAsc(ReferralCommissionEntity::getCreatedAt)); }
    default List<Object[]> findTopReferrersByEarnings() { Map<String, BigDecimal> totals = selectList(new LambdaQueryWrapper<ReferralCommissionEntity>().in(ReferralCommissionEntity::getStatus, "APPROVED", "PAID")).stream().collect(Collectors.groupingBy(ReferralCommissionEntity::getReferrerId, LinkedHashMap::new, Collectors.reducing(BigDecimal.ZERO, ReferralCommissionEntity::getCommission, BigDecimal::add))); return totals.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).map(e -> new Object[]{e.getKey(), e.getValue()}).toList(); }
    private static BigDecimal sum(List<ReferralCommissionEntity> list) { return list.stream().map(ReferralCommissionEntity::getCommission).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add); }
}
