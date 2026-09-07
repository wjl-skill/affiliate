package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 实时竞价拍卖成交事实持久化实体 (Auction MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `auction`。
 */
@TableName("auction")
public class AuctionEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String requestId;

    private String adSlotId;

    private String creativeId;

    private Double clearingPrice;

    private String currency;

    private String advertiser;

    private Instant createdAt;

    public AuctionEntity() {}

    public AuctionEntity(String id, String tenantId, String requestId, String adSlotId, String creativeId,
                         Double clearingPrice, String currency, String advertiser, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.requestId = requestId;
        this.adSlotId = adSlotId;
        this.creativeId = creativeId;
        this.clearingPrice = clearingPrice;
        this.currency = currency;
        this.advertiser = advertiser;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getAdSlotId() { return adSlotId; }
    public void setAdSlotId(String adSlotId) { this.adSlotId = adSlotId; }

    public String getCreativeId() { return creativeId; }
    public void setCreativeId(String creativeId) { this.creativeId = creativeId; }

    public Double getClearingPrice() { return clearingPrice; }
    public void setClearingPrice(Double clearingPrice) { this.clearingPrice = clearingPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getAdvertiser() { return advertiser; }
    public void setAdvertiser(String advertiser) { this.advertiser = advertiser; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
