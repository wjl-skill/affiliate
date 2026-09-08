package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.TouchPointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 触点数据访问层
 */
@Repository
public interface TouchPointRepository extends JpaRepository<TouchPointEntity, String> {

    /**
     * 查找用户在指定时间窗口内的所有触点
     */
    @Query("SELECT t FROM TouchPointEntity t WHERE t.userId = :userId " +
           "AND t.timestamp >= :windowStart AND t.timestamp < :windowEnd " +
           "ORDER BY t.timestamp ASC")
    List<TouchPointEntity> findTouchPointsInWindow(
            @Param("userId") String userId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    /**
     * 查找用户的最近 N 个触点
     */
    @Query("SELECT t FROM TouchPointEntity t WHERE t.userId = :userId " +
           "ORDER BY t.timestamp DESC LIMIT :limit")
    List<TouchPointEntity> findRecentTouchPoints(
            @Param("userId") String userId,
            @Param("limit") int limit
    );

    /**
     * 按渠道统计触点数量
     */
    @Query("SELECT t.affiliateId, COUNT(t) FROM TouchPointEntity t " +
           "WHERE t.userId = :userId GROUP BY t.affiliateId")
    List<Object[]> countTouchPointsByAffiliate(@Param("userId") String userId);

    /**
     * 删除过期触点（数据清理）
     */
    void deleteByTimestampBefore(Instant threshold);

    /**
     * 统计渠道的触点数量
     */
    long countByAffiliateId(String affiliateId);
}
