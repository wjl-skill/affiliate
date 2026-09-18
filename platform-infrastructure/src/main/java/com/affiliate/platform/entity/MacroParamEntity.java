package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 标准追踪宏参数字典实体 (Standard Macro Parameter Dictionary MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `affiliate_macro_param`。宏字典为平台级全局配置（跨租户共享），
 * 定义本网盟平台追踪链接与 Postback 回传中使用的标准宏（如 click_id、sub1..sub5）。
 */
@TableName("affiliate_macro_param")
public class MacroParamEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String macroKey;
    private String displayName;
    private String description;
    private String sampleValue;
    private String category;
    private String status;
    private Instant createdAt;

    public MacroParamEntity() {}

    public MacroParamEntity(String id, String macroKey, String displayName, String description,
                            String sampleValue, String category, String status, Instant createdAt) {
        this.id = id;
        this.macroKey = macroKey;
        this.displayName = displayName;
        this.description = description;
        this.sampleValue = sampleValue;
        this.category = category;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMacroKey() { return macroKey; }
    public void setMacroKey(String macroKey) { this.macroKey = macroKey; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSampleValue() { return sampleValue; }
    public void setSampleValue(String sampleValue) { this.sampleValue = sampleValue; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
