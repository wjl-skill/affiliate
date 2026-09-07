package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 广告物料素材持久化实体 (Creative MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `creative`。
 */
@TableName("creative")
public class CreativeEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String name;

    private String type;

    private String assetUrl;

    private String landingUrl;

    private Integer width;

    private Integer height;

    @TableField("categories")
    private String categories; // JSONB

    private Boolean active;

    private Instant createdAt;

    public CreativeEntity() {}

    public CreativeEntity(String id, String tenantId, String name, String type, String assetUrl, String landingUrl,
                          Integer width, Integer height, String categories, Boolean active, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.type = type;
        this.assetUrl = assetUrl;
        this.landingUrl = landingUrl;
        this.width = width;
        this.height = height;
        this.categories = categories;
        this.active = active;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAssetUrl() { return assetUrl; }
    public void setAssetUrl(String assetUrl) { this.assetUrl = assetUrl; }

    public String getLandingUrl() { return landingUrl; }
    public void setLandingUrl(String landingUrl) { this.landingUrl = landingUrl; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public String getCategories() { return categories; }
    public void setCategories(String categories) { this.categories = categories; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
