package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 商品实体类
 */
@Entity
@Table(name = "affiliate_product", indexes = {
        @Index(name = "idx_product_offer", columnList = "offer_id"),
        @Index(name = "idx_product_category", columnList = "category_id"),
        @Index(name = "idx_product_availability", columnList = "availability"),
        @Index(name = "idx_product_updated", columnList = "updated_at")
})
public class ProductEntity {

    @Id
    @Column(name = "sku", length = 128)
    private String sku;

    @Column(name = "offer_id", nullable = false, length = 64)
    private String offerId;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, length = 20)
    private String price;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "product_url", length = 1000)
    private String productUrl;

    @Column(name = "brand", length = 200)
    private String brand;

    @Column(name = "category_id", length = 64)
    private String categoryId;

    @Column(name = "availability", nullable = false, length = 20)
    private String availability; // IN_STOCK, OUT_OF_STOCK, BACKORDER, DISCONTINUED

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @Column(name = "attributes", columnDefinition = "JSONB")
    private String attributes; // JSON 格式的自定义属性

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ========== Constructors ==========

    public ProductEntity() {
    }

    public ProductEntity(String sku, String offerId, String name, String description,
                        String price, String currency, String imageUrl, String productUrl,
                        String brand, String categoryId, String availability,
                        Integer stockQuantity, String attributes,
                        Instant createdAt, Instant updatedAt) {
        this.sku = sku;
        this.offerId = offerId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.imageUrl = imageUrl;
        this.productUrl = productUrl;
        this.brand = brand;
        this.categoryId = categoryId;
        this.availability = availability;
        this.stockQuantity = stockQuantity;
        this.attributes = attributes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ========== Getters and Setters ==========

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
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

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (stockQuantity == null) {
            stockQuantity = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
