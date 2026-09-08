package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.cache.CacheKeyGenerator;
import com.affiliate.platform.affiliate.cache.MultiLevelCacheManager;
import com.affiliate.platform.affiliate.domain.ProductEntity;
import com.affiliate.platform.affiliate.repository.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 商品 Feed 服务（重构版 - 接入 PostgreSQL + 多级缓存）
 */
@Service
public class ProductFeedService {

    private final ProductRepository productRepository;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    private static final Duration PRODUCT_CACHE_TTL = Duration.ofHours(2);
    private static final Duration PRODUCT_LIST_CACHE_TTL = Duration.ofMinutes(15);

    public ProductFeedService(
            ProductRepository productRepository,
            MultiLevelCacheManager cacheManager,
            CacheKeyGenerator keyGenerator,
            ObjectMapper objectMapper
    ) {
        this.productRepository = productRepository;
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 添加单个商品
     */
    @Transactional
    public Product addProduct(
            String offerId,
            String sku,
            String name,
            String description,
            String price,
            String currency,
            String imageUrl,
            String productUrl,
            String brand,
            String categoryId,
            int stockQuantity,
            Map<String, String> attributes
    ) {
        ProductAvailability availability = stockQuantity > 0 ?
                ProductAvailability.IN_STOCK : ProductAvailability.OUT_OF_STOCK;

        ProductEntity entity = new ProductEntity(
                sku,
                offerId,
                name,
                description,
                price,
                currency,
                imageUrl,
                productUrl,
                brand,
                categoryId,
                availability.name(),
                stockQuantity,
                serializeAttributes(attributes),
                Instant.now(),
                Instant.now()
        );

        productRepository.save(entity);

        // 失效缓存
        evictProductCaches(offerId, sku);

        return toProduct(entity);
    }

    /**
     * 获取商品详情（使用多级缓存）
     */
    public Optional<Product> getProduct(String sku) {
        String cacheKey = keyGenerator.product(sku);

        return cacheManager.get(
                cacheKey,
                ProductEntity.class,
                PRODUCT_CACHE_TTL,
                () -> productRepository.findById(sku).orElse(null)
        ).map(this::toProduct);
    }

    /**
     * 获取 Offer 的所有商品（带缓存）
     */
    public List<Product> getProductsByOffer(String offerId) {
        String cacheKey = keyGenerator.productsByOffer(offerId);

        return cacheManager.get(
                cacheKey,
                List.class,
                PRODUCT_LIST_CACHE_TTL,
                () -> productRepository.findByOfferIdOrderByNameAsc(offerId)
        ).map(list -> ((List<ProductEntity>) list).stream()
                .map(this::toProduct)
                .collect(Collectors.toList())
        ).orElse(List.of());
    }

    /**
     * 分页查询商品
     */
    public Page<Product> getProductsByOfferPaged(String offerId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("name").ascending());

        return productRepository.findByOfferId(offerId, pageRequest)
                .map(this::toProduct);
    }

    /**
     * 更新商品库存
     */
    @Transactional
    public Product updateStock(String sku, int newQuantity) {
        ProductEntity entity = productRepository.findById(sku)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + sku));

        ProductAvailability availability = newQuantity > 0 ?
                ProductAvailability.IN_STOCK : ProductAvailability.OUT_OF_STOCK;

        entity.setStockQuantity(newQuantity);
        entity.setAvailability(availability.name());
        entity.setUpdatedAt(Instant.now());

        productRepository.save(entity);

        // 失效缓存
        evictProductCaches(entity.getOfferId(), sku);

        return toProduct(entity);
    }

    /**
     * 搜索商品
     */
    public List<Product> searchProducts(
            String offerId,
            String keyword,
            String categoryId,
            String brand,
            ProductAvailability availability
    ) {
        List<ProductEntity> results;

        if (keyword != null && !keyword.isEmpty()) {
            results = productRepository.searchByKeyword(offerId, keyword);
        } else if (categoryId != null) {
            results = productRepository.findByCategoryIdOrderByNameAsc(categoryId);
        } else if (brand != null) {
            results = productRepository.findByBrandOrderByNameAsc(brand);
        } else {
            results = productRepository.findByOfferIdOrderByNameAsc(offerId);
        }

        // 过滤可用性
        if (availability != null) {
            results = results.stream()
                    .filter(p -> p.getAvailability().equals(availability.name()))
                    .collect(Collectors.toList());
        }

        return results.stream()
                .map(this::toProduct)
                .collect(Collectors.toList());
    }

    /**
     * 获取缺货商品
     */
    public List<Product> getOutOfStockProducts(String offerId) {
        return productRepository.findByOfferIdAndAvailability(
                        offerId,
                        ProductAvailability.OUT_OF_STOCK.name()
                ).stream()
                .map(this::toProduct)
                .collect(Collectors.toList());
    }

    /**
     * 批量更新商品状态
     */
    @Transactional
    public void bulkUpdateAvailability(List<String> skus, ProductAvailability availability) {
        int quantity = availability == ProductAvailability.IN_STOCK ? 1 : 0;

        productRepository.bulkUpdateStock(skus, quantity, availability.name());

        // 失效缓存（批量）
        for (String sku : skus) {
            productRepository.findById(sku).ifPresent(entity ->
                    evictProductCaches(entity.getOfferId(), sku)
            );
        }
    }

    /**
     * 生成商品 Deep-link
     */
    public String generateDeepLink(
            String sku,
            String affiliateId,
            String clickTrackerBaseUrl,
            Map<String, String> additionalParams
    ) {
        ProductEntity product = productRepository.findById(sku)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + sku));

        StringBuilder deepLink = new StringBuilder(clickTrackerBaseUrl);
        deepLink.append("?offer_id=").append(product.getOfferId());
        deepLink.append("&aff_id=").append(affiliateId);
        deepLink.append("&product_sku=").append(sku);

        if (additionalParams != null) {
            for (Map.Entry<String, String> entry : additionalParams.entrySet()) {
                deepLink.append("&").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }

        return deepLink.toString();
    }

    /**
     * 统计 Offer 商品数量
     */
    public long countProducts(String offerId) {
        return productRepository.countByOfferId(offerId);
    }

    /**
     * 统计缺货商品数量
     */
    public long countOutOfStockProducts(String offerId) {
        return productRepository.countByOfferIdAndAvailability(
                offerId,
                ProductAvailability.OUT_OF_STOCK.name()
        );
    }

    /**
     * 删除商品
     */
    @Transactional
    public void deleteProduct(String sku) {
        productRepository.findById(sku).ifPresent(entity -> {
            productRepository.deleteById(sku);
            evictProductCaches(entity.getOfferId(), sku);
        });
    }

    /**
     * 删除 Offer 的所有商品
     */
    @Transactional
    public void deleteAllProductsByOffer(String offerId) {
        productRepository.deleteByOfferId(offerId);

        // 失效缓存
        cacheManager.evictByPattern(keyGenerator.patternByPrefix("product", "offer", offerId));
    }

    // ========== 私有辅助方法 ==========

    private void evictProductCaches(String offerId, String sku) {
        // 失效单个商品缓存
        cacheManager.evict(keyGenerator.product(sku));

        // 失效商品列表缓存
        cacheManager.evict(keyGenerator.productsByOffer(offerId));
    }

    private String serializeAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, String> deserializeAttributes(String json) {
        if (json == null || json.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private Product toProduct(ProductEntity entity) {
        return new Product(
                entity.getSku(),
                entity.getOfferId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getCurrency(),
                entity.getImageUrl(),
                entity.getProductUrl(),
                entity.getBrand(),
                entity.getCategoryId(),
                ProductAvailability.valueOf(entity.getAvailability()),
                entity.getStockQuantity(),
                deserializeAttributes(entity.getAttributes()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    // ========== 数据记录 ==========

    public record Product(
            String sku,
            String offerId,
            String name,
            String description,
            String price,
            String currency,
            String imageUrl,
            String productUrl,
            String brand,
            String categoryId,
            ProductAvailability availability,
            int stockQuantity,
            Map<String, String> attributes,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public enum ProductAvailability {
        IN_STOCK,
        OUT_OF_STOCK,
        BACKORDER,
        DISCONTINUED
    }
}
