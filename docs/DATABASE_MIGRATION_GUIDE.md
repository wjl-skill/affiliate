# 数据库接入与多级缓存架构实施指南

## 概述

本文档说明网盟营销平台如何使用 PostgreSQL + MyBatis-Plus + 多级缓存。生产读写统一经过 MyBatis-Plus Mapper；内存实现只在未启用数据库的本地测试或显式降级场景使用。

## 架构设计

### 三级缓存架构

```
┌─────────────────────────────────────────────────────────────┐
│                     应用层 (Service)                          │
├─────────────────────────────────────────────────────────────┤
│  L1 Cache (Caffeine)                                        │
│  - 本地 JVM 内存缓存                                          │
│  - 容量：10,000 条目                                          │
│  - TTL：写入后 5 分钟，访问后 10 分钟                          │
│  - 延迟：<1ms                                                 │
├─────────────────────────────────────────────────────────────┤
│  L2 Cache (Redis)                                           │
│  - 分布式缓存，多实例共享                                      │
│  - TTL：可配置，默认 30 分钟                                   │
│  - 延迟：1-10ms                                               │
├─────────────────────────────────────────────────────────────┤
│  L3 Storage (PostgreSQL)                                    │
│  - 持久化存储                                                 │
│  - 延迟：10-100ms                                             │
│  - 索引优化：B-tree、JSONB GIN                                │
└─────────────────────────────────────────────────────────────┘
```

### 数据流向

**读取路径**：
```
Service → L1 (命中) → 返回
         ↓ (未命中)
Service → L2 (命中) → 回填 L1 → 返回
         ↓ (未命中)
Service → L3 (数据库) → 回填 L2 → 回填 L1 → 返回
```

**写入路径**：
```
Service → L3 (数据库) → 失效 L2 → 失效 L1
```

## 已实现的组件

### 1. 缓存基础设施

#### MultiLevelCacheManager
- 位置：`platform-affiliate/src/main/java/com/affiliate/platform/affiliate/cache/MultiLevelCacheManager.java`
- 功能：
  - 三级缓存自动穿透
  - 缓存回填
  - 失效策略（单键、批量、模式匹配）
  - L1 缓存统计

#### CacheKeyGenerator
- 位置：`platform-affiliate/src/main/java/com/affiliate/platform/affiliate/cache/CacheKeyGenerator.java`
- 功能：
  - 统一缓存键命名规范
  - 防止键冲突
  - 支持 60+ 业务实体的缓存键生成

### 2. 数据访问层示例

#### API Key 管理（完整实现）

**Mapper**：
```java
// AffiliateApiKeyMapper.java
- findBySecretKey(String secretKey)
- findByAffiliateIdOrderByCreatedAtDesc(String affiliateId)
- findExpiringKeys(Instant threshold)
- findExpiredKeys(Instant now)
```

**Entity**：
```java
// ApiKeyEntity.java
- MyBatis-Plus 实体类（@TableName、@TableId）
- 索引：secret_key (UNIQUE), affiliate_id, status, expires_at
- 时间字段由服务层和数据库默认值维护
```

**Service V2**：
```java
// ApiKeyManagementService.java
- 高频验证操作使用 L1 快速路径
- 创建/更新/删除操作自动失效缓存
- 异步更新使用计数
```

#### Product Feed（完整实现）

**Mapper**：
```java
// ProductRepository.java
- 分页查询
- 关键词搜索
- 批量更新
```

**Entity**：
```java
// ProductEntity.java
- JSONB 字段存储自定义属性
- 自动更新 updated_at
```

**Service V2**：
```java
// ProductFeedService.java
- 商品列表缓存 15 分钟
- 单个商品缓存 2 小时
- 批量操作自动失效相关缓存
```

### 3. 数据库表结构

**SQL 脚本**：`docs/sql/14_affiliate_extended_tables.sql`

生产环境使用 `platform-infrastructure/src/main/resources/db/migration/V1__...` 至 `V9__...` 的 Flyway 版本脚本；`docs/sql` 目录仅保留为历史初始化脚本和字段对照参考。

包含表：
- `affiliate_api_key` - API 密钥管理
- `affiliate_payment_method` - 支付方式
- `affiliate_payment_transaction` - 支付交易
- `affiliate_product` - 商品目录
- `affiliate_product_feed` - Feed 配置
- `affiliate_product_category` - 商品分类
- `affiliate_ip_geolocation_cache` - IP 地理位置缓存
- `affiliate_touch_point` - 归因触点（分区表）
- `affiliate_attribution_result` - 归因结果
- `affiliate_notification` - 通知消息
- `affiliate_webhook_endpoint` - Webhook 配置
- `affiliate_creative` - 素材管理
- `affiliate_referral_relationship` - 推荐关系
- `affiliate_referral_commission` - 推荐佣金
- `affiliate_terms_acceptance` - 条款接受
- `affiliate_tax_document` - 税务文档
- `affiliate_kyc_verification` - KYC 验证
- `affiliate_compliance_violation` - 合规违规
- `affiliate_performance_snapshot` - 效果报表快照

## 生产持久化状态

### 高优先级（核心热路径）

1. **ConversionAttributionService**（已完成）
   - 表：`affiliate_touch_point`（分区）、`affiliate_attribution_result`
   - 缓存策略：归因结果缓存 1 小时，触点历史不缓存（写多读少）

2. **GeolocationService**（已完成）
   - 表：`affiliate_ip_geolocation_cache`
   - 缓存策略：IP 查询结果缓存 24 小时（外部 API 调用昂贵）

3. **PaymentGatewayService**（已完成）
   - 表：`affiliate_payment_method`、`affiliate_payment_transaction`
   - 缓存策略：支付方式缓存 1 小时，交易记录不缓存（强一致性）

### 中优先级

4. **AffiliateNotificationService**（已完成）
   - 表：`affiliate_notification`、`affiliate_notification_preference`、`affiliate_webhook_endpoint`
   - 缓存策略：未读通知缓存 5 分钟，偏好配置缓存 30 分钟

5. **AffiliateCreativeService**（已完成）
   - 表：`affiliate_creative`、`affiliate_creative_performance`
   - 缓存策略：素材详情缓存 1 小时，性能数据缓存 10 分钟

6. **ReferralCommissionService**（已完成）
   - 表：`affiliate_referral_relationship`、`affiliate_referral_commission`
   - 缓存策略：推荐关系缓存 30 分钟，佣金流水不缓存

7. **ComplianceTrackingService**（已完成）
   - 表：`affiliate_terms_acceptance`、`affiliate_tax_document`、`affiliate_kyc_verification`、`affiliate_compliance_violation`
   - 缓存策略：KYC 状态缓存 1 小时，合规风险评分缓存 30 分钟

### 低优先级

8. **PerformanceReportService**（已完成）
   - 表：`affiliate_performance_snapshot`
   - 缓存策略：报表快照缓存 1 小时（预聚合表）

9. **OfferApprovalWorkflowService**（共享基础设施 Mapper）
   - 继续使用现有 `affiliate_offer_access` 表
   - 缓存策略：访问权限缓存 15 分钟

## 改造模板

### Service 改造步骤

1. **创建 MyBatis-Plus Entity 类**：
```java
@TableName("table_name")
public class EntityClass {
    @TableId(type = IdType.INPUT)
    private String id;
    // 字段定义
}
```

2. **创建 Mapper 接口**：
```java
@Mapper
public interface EntityMapper extends BaseMapper<EntityClass> {
    // 使用 LambdaQueryWrapper 或 @Select/@Update 声明 SQL
}
```

3. **在现有 Service 中接入 Mapper 与缓存**：
```java
@Service
public class Service {
    private final EntityMapper mapper;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;

    // 读操作：使用缓存
    public Optional<Entity> get(String id) {
        return cacheManager.get(
            keyGenerator.entityById(id),
            EntityClass.class,
            CACHE_TTL,
            () -> mapper.selectById(id)
        ).map(this::toDto);
    }

    // 写操作：写入数据库并失效缓存
    @Transactional
    public Entity create(CreateRequest request) {
        EntityClass entity = new EntityClass(...);
        mapper.insert(entity);

        // 失效相关缓存
        cacheManager.evict(keyGenerator.entityById(entity.getId()));

        return toDto(entity);
    }
}
```

### 缓存 TTL 建议

| 数据类型 | TTL | 原因 |
|---------|-----|------|
| 用户配置 | 30 分钟 - 1 小时 | 变更频率低 |
| 统计数据 | 5 - 15 分钟 | 准实时即可 |
| 热点数据 | 10 - 30 分钟 | 高频访问 |
| API 验证 | 5 - 10 分钟 | 安全敏感 |
| 外部 API 结果 | 1 - 24 小时 | 调用成本高 |
| 事务数据 | 不缓存 | 强一致性要求 |

## 性能优化建议

### 数据库层

1. **索引优化**：
   - 为所有 WHERE、JOIN、ORDER BY 字段创建索引
   - 使用复合索引覆盖多字段查询
   - JSONB 字段使用 GIN 索引

2. **分区表**：
   - 高频写入表（touch_point、click_session、conversion）按月分区
   - 自动分区维护脚本

3. **连接池配置**：
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

### 缓存层

1. **Caffeine 配置**：
```java
Caffeine.newBuilder()
    .maximumSize(10000)
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .recordStats()  // 启用统计
```

2. **Redis 配置**：
```properties
spring.redis.jedis.pool.max-active=20
spring.redis.jedis.pool.max-idle=10
spring.redis.jedis.pool.min-idle=5
spring.redis.timeout=3000
```

3. **缓存预热**：
   - 启动时加载热点数据到 L1 和 L2
   - 定时刷新高频查询的缓存

### 监控指标

1. **缓存命中率**：
   - L1 命中率目标：>80%
   - L2 命中率目标：>90%
   - 总命中率目标：>95%

2. **数据库性能**：
   - 慢查询：>100ms 的查询需要优化
   - 连接池使用率：<80%
   - 索引命中率：>95%

3. **API 响应时间**：
   - P95 < 50ms（缓存命中）
   - P99 < 200ms（数据库查询）

## 部署检查清单

- [ ] 启用 Flyway 并执行 V1–V9 版本迁移
- [ ] 验证所有索引已创建
- [ ] 配置 Redis 连接
- [ ] 配置数据库连接池
- [ ] 启用 Flyway 自动迁移
- [ ] 验证缓存配置
- [ ] 运行集成测试
- [ ] 监控缓存命中率
- [ ] 检查慢查询日志
- [ ] 配置备份策略

## 总结

当前已完成：
- ✅ 多级缓存基础设施
- ✅ 缓存键生成器
- ✅ API Key、Product Feed 及联盟扩展业务的 MyBatis-Plus 改造
- ✅ DMP/CDP、预算、事件 Outbox、Cohort/日报表生产 Mapper
- ✅ Flyway V7–V9 增量迁移及旧表兼容补列
- ✅ 完整的 MyBatis-Plus 改造模板和指南

后续增强：
- ⏳ 缓存预热机制
- ⏳ 监控告警配置
- ⏳ 压力测试验证
