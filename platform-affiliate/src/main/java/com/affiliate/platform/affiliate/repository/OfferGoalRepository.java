package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.entity.OfferGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Offer目标（转化事件）数据访问层
 */
@Repository
public interface OfferGoalRepository extends JpaRepository<OfferGoalEntity, String> {

    /**
     * 根据Offer ID查找所有目标
     */
    List<OfferGoalEntity> findByOfferIdOrderByCreatedAtDesc(String offerId);

    /**
     * 根据Offer ID和状态查找目标
     */
    List<OfferGoalEntity> findByOfferIdAndStatusOrderByCreatedAtDesc(String offerId, String status);

    /**
     * 根据目标类型查找
     */
    List<OfferGoalEntity> findByGoalTypeOrderByCreatedAtDesc(String goalType);

    /**
     * 根据状态查找所有目标
     */
    List<OfferGoalEntity> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * 查找特定Offer的活跃目标
     */
    @Query("SELECT g FROM OfferGoalEntity g WHERE g.offerId = :offerId AND g.status = 'ACTIVE' ORDER BY g.createdAt DESC")
    List<OfferGoalEntity> findActiveGoalsByOfferId(@Param("offerId") String offerId);

    /**
     * 根据Offer ID和目标名称查找
     */
    @Query("SELECT g FROM OfferGoalEntity g WHERE g.offerId = :offerId AND g.goalName = :goalName")
    Optional<OfferGoalEntity> findByOfferIdAndGoalName(
            @Param("offerId") String offerId,
            @Param("goalName") String goalName
    );

    /**
     * 统计Offer的目标数量
     */
    @Query("SELECT COUNT(g) FROM OfferGoalEntity g WHERE g.offerId = :offerId")
    long countByOfferId(@Param("offerId") String offerId);

    /**
     * 统计Offer的活跃目标数量
     */
    @Query("SELECT COUNT(g) FROM OfferGoalEntity g WHERE g.offerId = :offerId AND g.status = 'ACTIVE'")
    long countActiveGoalsByOfferId(@Param("offerId") String offerId);

    /**
     * 批量更新状态
     */
    @Modifying
    @Query("UPDATE OfferGoalEntity g SET g.status = :status WHERE g.offerId = :offerId")
    int updateStatusByOfferId(@Param("offerId") String offerId, @Param("status") String status);

    /**
     * 删除特定Offer的所有目标
     */
    void deleteByOfferId(String offerId);

    /**
     * 检查Offer是否有目标
     */
    boolean existsByOfferId(String offerId);

    /**
     * 检查目标名称是否存在
     */
    boolean existsByOfferIdAndGoalName(String offerId, String goalName);
}
