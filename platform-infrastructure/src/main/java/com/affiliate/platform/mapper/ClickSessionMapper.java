package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.ClickSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 点击追踪会话数据访问层 Mapper (Click Session Mapper)
 */
@Mapper
public interface ClickSessionMapper extends BaseMapper<ClickSessionEntity> {

    /**
     * 高并发批量插入点击会话并自动忽略已存在主键 (PostgreSQL ON CONFLICT (click_id) DO NOTHING)
     */
    @Insert("""
        <script>
        INSERT INTO affiliate_click_session (
            click_id, tenant_id, offer_id, affiliate_id, sub1, sub2, sub3, sub4, sub5,
            ip, user_agent, country, device_type, created_at, expires_at
        ) VALUES
        <foreach collection='list' item='item' separator=','>
            (#{item.clickId}, #{item.tenantId}, #{item.offerId}, #{item.affiliateId},
             #{item.sub1}, #{item.sub2}, #{item.sub3}, #{item.sub4}, #{item.sub5},
             #{item.ip}, #{item.userAgent}, #{item.country}, #{item.deviceType},
             #{item.createdAt}, #{item.expiresAt})
        </foreach>
        ON CONFLICT (click_id) DO NOTHING
        </script>
    """)
    int insertBatchIgnore(@Param("list") List<ClickSessionEntity> list);
}
