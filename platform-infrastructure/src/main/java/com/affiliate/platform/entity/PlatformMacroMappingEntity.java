package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 第三方平台宏参数映射实体 (Platform Macro Mapping MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `affiliate_platform_macro_mapping`。
 * 记录广告主/流量平台 (Awin、ShareASale、CJ 等) 的宏写法与本平台标准宏 (macroKey) 的对照关系，
 * 用于追踪链接与 S2S 回传链接的双向宏渲染。
 */
@TableName("affiliate_platform_macro_mapping")
public class PlatformMacroMappingEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String platformCode;
    private String platformName;
    private String macroKey;
    private String platformMacroToken;
    private String remark;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public PlatformMacroMappingEntity() {}

    public PlatformMacroMappingEntity(String id, String platformCode, String platformName,
                                      String macroKey, String platformMacroToken, String remark,
                                      String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.platformCode = platformCode;
        this.platformName = platformName;
        this.macroKey = macroKey;
        this.platformMacroToken = platformMacroToken;
        this.remark = remark;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlatformCode() { return platformCode; }
    public void setPlatformCode(String platformCode) { this.platformCode = platformCode; }

    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }

    public String getMacroKey() { return macroKey; }
    public void setMacroKey(String macroKey) { this.macroKey = macroKey; }

    public String getPlatformMacroToken() { return platformMacroToken; }
    public void setPlatformMacroToken(String platformMacroToken) { this.platformMacroToken = platformMacroToken; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
