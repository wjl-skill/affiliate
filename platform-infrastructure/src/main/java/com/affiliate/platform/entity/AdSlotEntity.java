package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 媒体广告位持久化实体 (AdSlot MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `ad_slot`。
 */
@TableName("ad_slot")
public class AdSlotEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String name;

    private Integer width;

    private Integer height;

    private Double floorPrice;

    private Boolean secure;

    private Boolean active;

    private Instant createdAt;

    public AdSlotEntity() {}

    public AdSlotEntity(String id, String tenantId, String name, Integer width, Integer height,
                        Double floorPrice, Boolean secure, Boolean active, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.width = width;
        this.height = height;
        this.floorPrice = floorPrice;
        this.secure = secure;
        this.active = active;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public Double getFloorPrice() { return floorPrice; }
    public void setFloorPrice(Double floorPrice) { this.floorPrice = floorPrice; }

    public Boolean getSecure() { return secure; }
    public void setSecure(Boolean secure) { this.secure = secure; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
