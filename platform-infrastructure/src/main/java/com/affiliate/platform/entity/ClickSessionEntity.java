package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 点击会话存根实体 (Click Session Entity)
 * <p>
 * 映射数据库表 `affiliate_click_session`。
 */
@TableName("affiliate_click_session")
public class ClickSessionEntity {

    @TableId(type = IdType.INPUT)
    private String clickId;
    private String tenantId;
    private String offerId;
    private String affiliateId;
    private String sub1;
    private String sub2;
    private String sub3;
    private String sub4;
    private String sub5;
    private String ip;
    private String userAgent;
    private String country;
    private Integer deviceType;
    private Instant createdAt;
    private Instant expiresAt;

    public ClickSessionEntity() {}

    public ClickSessionEntity(String clickId, String tenantId, String offerId, String affiliateId,
                              String sub1, String sub2, String sub3, String sub4, String sub5,
                              String ip, String userAgent, String country, Integer deviceType,
                              Instant createdAt, Instant expiresAt) {
        this.clickId = clickId;
        this.tenantId = tenantId;
        this.offerId = offerId;
        this.affiliateId = affiliateId;
        this.sub1 = sub1;
        this.sub2 = sub2;
        this.sub3 = sub3;
        this.sub4 = sub4;
        this.sub5 = sub5;
        this.ip = ip;
        this.userAgent = userAgent;
        this.country = country;
        this.deviceType = deviceType;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getClickId() { return clickId; }
    public void setClickId(String clickId) { this.clickId = clickId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public String getAffiliateId() { return affiliateId; }
    public void setAffiliateId(String affiliateId) { this.affiliateId = affiliateId; }

    public String getSub1() { return sub1; }
    public void setSub1(String sub1) { this.sub1 = sub1; }

    public String getSub2() { return sub2; }
    public void setSub2(String sub2) { this.sub2 = sub2; }

    public String getSub3() { return sub3; }
    public void setSub3(String sub3) { this.sub3 = sub3; }

    public String getSub4() { return sub4; }
    public void setSub4(String sub4) { this.sub4 = sub4; }

    public String getSub5() { return sub5; }
    public void setSub5(String sub5) { this.sub5 = sub5; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Integer getDeviceType() { return deviceType; }
    public void setDeviceType(Integer deviceType) { this.deviceType = deviceType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
