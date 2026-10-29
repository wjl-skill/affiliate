package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.ConversionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 转化归因数据访问层 Mapper (Conversion Mapper)
 */
@Mapper
public interface ConversionMapper extends BaseMapper<ConversionEntity> {

    /**
     * 高并发批量插入转化并支持冲突更新 (PostgreSQL ON CONFLICT (id) DO UPDATE)
     */
    @Insert("""
        <script>
        INSERT INTO affiliate_conversion (
            id, tenant_id, click_id, offer_id, affiliate_id, transaction_id, payout, revenue, sale_amount,
            status, ctit_seconds, rejection_reason, sub1, postback_status, created_at
        ) VALUES
        <foreach collection='list' item='item' separator=','>
            (#{item.id}, #{item.tenantId}, #{item.clickId}, #{item.offerId}, #{item.affiliateId},
             #{item.transactionId}, #{item.payout}, #{item.revenue}, #{item.saleAmount},
             #{item.status}, #{item.ctitSeconds}, #{item.rejectionReason}, #{item.sub1},
             #{item.postbackStatus}, #{item.createdAt})
        </foreach>
        ON CONFLICT (id) DO UPDATE SET
            status = EXCLUDED.status,
            postback_status = EXCLUDED.postback_status,
            rejection_reason = EXCLUDED.rejection_reason
        </script>
    """)
    int upsertBatch(@Param("list") List<ConversionEntity> list);
}
