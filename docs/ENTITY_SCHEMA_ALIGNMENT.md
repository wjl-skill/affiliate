# 实体与数据库结构对齐

生产数据库结构以 `platform-infrastructure/src/main/resources/db/migration/V1__*.sql` 至最新版本为唯一基准，`docs/sql` 仅用于本地初始化和结构查阅。实体列名使用 MyBatis-Plus 的显式 `@TableField` 或驼峰转下划线规则。

## 本次修复

- `ConversionEntity` 与 `affiliate_conversion` 完整对齐：使用 `tx_id`、`sale_amount`、`rejection_reason`，移除不存在的 `transaction_id`、`sub1`、`postback_status`。S2S 保存和回读现在保留销售金额及拒绝原因。
- `AffiliateInvoiceEntity` 增加 `conversionCount`，并映射新增的 `billing_cycle`、`paid_at`。结算列表不再把转化数固定返回为 1。
- `BillingEntryEntity` 对齐 V1/V3 的账本字段：UUID 主键、`auction_id`、`entry_type`、`description`、`occurred_at`。JdbcBillingService 保存和查询时保留这些业务字段。
- `OfferEntity` 增加 PostgreSQL `text[]` 和 `integer[]` 类型处理器，国家和设备定向可以持久化并正确回读。
- `CdpUserTraitEntity.events7dCount` 显式映射到 `events_7d_count`。
- V10 补充历史数据库缺失的 `dmp_segment_member`、发票周期和支付时间列；V1-V9 不被修改。
- V20 新增 Offer 流量补量规则与审计日志表（`affiliate_offer_supplement_rule`, `affiliate_offer_supplement_log`）。
- V21 核心高频业务索引加固调优。
- V22 新增 PostgreSQL 按月范围分区表与冷数据归档表（`affiliate_click_session_partitioned`, `affiliate_offer_supplement_log_partitioned`, `affiliate_click_session_archive`, `affiliate_offer_supplement_log_archive`），配套 4 个 MyBatis-Plus 实体与 Mapper，保持 88 张表 100% 对齐。

## 持续审计

运行以下命令会扫描所有 `*Entity.java`，解析 `@TableName`/`@Table`、`@TableField`/`@Column`，并将列集合与累计 Flyway 表结构比较：

```powershell
rtk node scripts/audit-entity-schema.mjs
```

脚本会报告实体独有列、表独有列以及缺少迁移表。提交实体或迁移变更时应先运行该审计，再运行 Maven 测试和构建。
