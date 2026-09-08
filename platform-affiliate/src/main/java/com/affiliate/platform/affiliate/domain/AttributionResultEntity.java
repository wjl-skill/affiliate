package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 归因结果实体
 */
@Entity
@Table(name = "affiliate_attribution_result", indexes = {
        @Index(name = "idx_attribution_user", columnList = "user_id"),
        @Index(name = "idx_attribution_conversion", columnList = "conversion_id", unique = true),
        @Index(name = "idx_attribution_time", columnList = "conversion_time")
})
public class AttributionResultEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "conversion_id", nullable = false, unique = true, length = 64)
    private String conversionId;

    @Column(name = "conversion_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal conversionValue;

    @Column(name = "conversion_time", nullable = false)
    private Instant conversionTime;

    @Column(name = "attribution_model", nullable = false, length = 50)
    private String attributionModel; // FIRST_CLICK, LAST_CLICK, LINEAR, TIME_DECAY, POSITION_BASED, DATA_DRIVEN

    @Column(name = "touch_point_count", nullable = false)
    private Integer touchPointCount;

    @Column(name = "credits", nullable = false, columnDefinition = "JSONB")
    private String credits; // JSON: [{"affiliateId":"aff1","clickId":"c1","weight":0.5,"creditedValue":50.00}]

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    // Constructors
    public AttributionResultEntity() {
    }

    public AttributionResultEntity(String id, String userId, String conversionId,
                                  BigDecimal conversionValue, Instant conversionTime,
                                  String attributionModel, Integer touchPointCount,
                                  String credits, Instant calculatedAt) {
        this.id = id;
        this.userId = userId;
        this.conversionId = conversionId;
        this.conversionValue = conversionValue;
        this.conversionTime = conversionTime;
        this.attributionModel = attributionModel;
        this.touchPointCount = touchPointCount;
        this.credits = credits;
        this.calculatedAt = calculatedAt;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConversionId() {
        return conversionId;
    }

    public void setConversionId(String conversionId) {
        this.conversionId = conversionId;
    }

    public BigDecimal getConversionValue() {
        return conversionValue;
    }

    public void setConversionValue(BigDecimal conversionValue) {
        this.conversionValue = conversionValue;
    }

    public Instant getConversionTime() {
        return conversionTime;
    }

    public void setConversionTime(Instant conversionTime) {
        this.conversionTime = conversionTime;
    }

    public String getAttributionModel() {
        return attributionModel;
    }

    public void setAttributionModel(String attributionModel) {
        this.attributionModel = attributionModel;
    }

    public Integer getTouchPointCount() {
        return touchPointCount;
    }

    public void setTouchPointCount(Integer touchPointCount) {
        this.touchPointCount = touchPointCount;
    }

    public String getCredits() {
        return credits;
    }

    public void setCredits(String credits) {
        this.credits = credits;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    @PrePersist
    public void prePersist() {
        if (calculatedAt == null) {
            calculatedAt = Instant.now();
        }
    }
}
