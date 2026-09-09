package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.TermsAcceptanceEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** MyBatis-Plus terms acceptance store. */
@Mapper
public interface TermsAcceptanceRepository extends BaseMapper<TermsAcceptanceEntity> {
    default TermsAcceptanceEntity save(TermsAcceptanceEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default List<TermsAcceptanceEntity> findByAffiliateIdOrderByAcceptedAtDesc(String affiliateId) {
        return selectList(new LambdaQueryWrapper<TermsAcceptanceEntity>().eq(TermsAcceptanceEntity::getAffiliateId, affiliateId)
                .orderByDesc(TermsAcceptanceEntity::getAcceptedAt));
    }
    default Optional<TermsAcceptanceEntity> findByAffiliateIdAndVersion(String affiliateId, String version) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<TermsAcceptanceEntity>().eq(TermsAcceptanceEntity::getAffiliateId, affiliateId)
                .eq(TermsAcceptanceEntity::getVersion, version)));
    }
    default boolean existsByAffiliateIdAndVersion(String affiliateId, String version) {
        return selectCount(new LambdaQueryWrapper<TermsAcceptanceEntity>().eq(TermsAcceptanceEntity::getAffiliateId, affiliateId)
                .eq(TermsAcceptanceEntity::getVersion, version)) > 0;
    }
    default List<String> findAffiliatesNotAcceptedVersion(String latestVersion) {
        List<TermsAcceptanceEntity> all = selectList(null);
        java.util.Set<String> allAffiliates = all.stream().map(TermsAcceptanceEntity::getAffiliateId).collect(Collectors.toSet());
        java.util.Set<String> accepted = all.stream().filter(e -> latestVersion.equals(e.getVersion()))
                .map(TermsAcceptanceEntity::getAffiliateId).collect(Collectors.toSet());
        allAffiliates.removeAll(accepted);
        return List.copyOf(allAffiliates);
    }
}
