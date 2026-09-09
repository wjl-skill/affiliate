package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * IP 地理位置缓存实体
 */
@Entity
@Table(name = "affiliate_ip_geolocation_cache", indexes = {
        @Index(name = "idx_geo_ip", columnList = "ip_address", unique = true),
        @Index(name = "idx_geo_country", columnList = "country_code"),
        @Index(name = "idx_geo_updated", columnList = "updated_at")
})
@com.baomidou.mybatisplus.annotation.TableName("affiliate_ip_geolocation_cache")
public class IpGeolocationCacheEntity {

    @Id
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "ip_address", nullable = false, unique = true, length = 45)
    private String ipAddress;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "country_name", length = 100)
    private String countryName;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "timezone", length = 50)
    private String timezone;

    @Column(name = "isp", length = 255)
    private String isp;

    @Column(name = "asn", length = 20)
    private String asn;

    @Column(name = "is_vpn")
    private Boolean isVpn;

    @Column(name = "is_proxy")
    private Boolean isProxy;

    @Column(name = "is_tor")
    private Boolean isTor;

    @Column(name = "is_datacenter")
    private Boolean isDatacenter;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors
    public IpGeolocationCacheEntity() {
    }

    public IpGeolocationCacheEntity(String id, String ipAddress, String countryCode,
                                   String countryName, String city, String region,
                                   Double latitude, Double longitude, String timezone,
                                   String isp, String asn, Boolean isVpn, Boolean isProxy,
                                   Boolean isTor, Boolean isDatacenter, Integer riskScore,
                                   String riskLevel, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.city = city;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timezone = timezone;
        this.isp = isp;
        this.asn = asn;
        this.isVpn = isVpn;
        this.isProxy = isProxy;
        this.isTor = isTor;
        this.isDatacenter = isDatacenter;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getIsp() {
        return isp;
    }

    public void setIsp(String isp) {
        this.isp = isp;
    }

    public String getAsn() {
        return asn;
    }

    public void setAsn(String asn) {
        this.asn = asn;
    }

    public Boolean getIsVpn() {
        return isVpn;
    }

    public void setIsVpn(Boolean isVpn) {
        this.isVpn = isVpn;
    }

    public Boolean getIsProxy() {
        return isProxy;
    }

    public void setIsProxy(Boolean isProxy) {
        this.isProxy = isProxy;
    }

    public Boolean getIsTor() {
        return isTor;
    }

    public void setIsTor(Boolean isTor) {
        this.isTor = isTor;
    }

    public Boolean getIsDatacenter() {
        return isDatacenter;
    }

    public void setIsDatacenter(Boolean isDatacenter) {
        this.isDatacenter = isDatacenter;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
