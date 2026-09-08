package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.AttributionResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 归因结果数据访问层
 */
@Repository
public interface AttributionResultRepository extends JpaRepository<AttributionResultEntity, String> {

    /**
     * 根据转化ID查找归因结果
     */
    Optional<AttributionResultEntity> findByConversionId(String conversionId);

    /**
     * 查找用户的所有归因结果
     */
    List<AttributionResultEntity> findByUserIdOrderByConversionTimeDesc(String userId);

    /**
     * 查找指定时间范围内的归因结果
     */
    @Query("SELECT a FROM AttributionResultEntity a WHERE a.conversionTime >= :from AND a.conversionTime < :to " +
           "ORDER BY a.conversionTime DESC")
    List<AttributionResultEntity> findByTimeRange(
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 按归因模型统计
     */
    @Query("SELECT a.attributionModel, COUNT(a) FROM AttributionResultEntity a GROUP BY a.attributionModel")
    List<Object[]> countByAttributionModel();

    /**
     * 检查转化ID是否已存在
     */
    boolean existsByConversionId(String conversionId);
}
