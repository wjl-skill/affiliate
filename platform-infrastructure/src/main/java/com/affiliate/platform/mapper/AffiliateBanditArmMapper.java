package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.AffiliateBanditArmEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 分布式 LinUCB 多臂老虎机模型参数 Mapper (Bandit Arm Mapper)
 */
@Mapper
public interface AffiliateBanditArmMapper extends BaseMapper<AffiliateBanditArmEntity> {

    @Insert("""
        INSERT INTO affiliate_bandit_arm (
            arm_key, tenant_id, smart_link_id, offer_id, dimension, a_inv_json, b_vec_json,
            sample_count, clicks, conversions, total_reward, updated_at
        ) VALUES (
            #{entity.armKey}, #{entity.tenantId}, #{entity.smartLinkId}, #{entity.offerId},
            #{entity.dimension}, #{entity.aInvJson}, #{entity.bVecJson},
            #{entity.sampleCount}, #{entity.clicks}, #{entity.conversions},
            #{entity.totalReward}, #{entity.updatedAt}
        )
        ON CONFLICT (arm_key) DO UPDATE SET
            a_inv_json = EXCLUDED.a_inv_json,
            b_vec_json = EXCLUDED.b_vec_json,
            sample_count = EXCLUDED.sample_count,
            clicks = EXCLUDED.clicks,
            conversions = EXCLUDED.conversions,
            total_reward = EXCLUDED.total_reward,
            updated_at = EXCLUDED.updated_at
    """)
    int upsert(@Param("entity") AffiliateBanditArmEntity entity);
}
