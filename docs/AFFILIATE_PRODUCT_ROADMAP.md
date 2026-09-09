# 网盟平台产品设计与实现路线图

更新时间：2026-09-09

## 1. 现状判断

当前项目是一个 Java 21 / Spring Boot 3.4 模块化单体，已覆盖 Offer、渠道客、点击追踪、S2S Postback、归因、SmartLink/TDS、反欺诈、Sub-ID 报表、结算和支付等主要边界，同时还包含 DSP、SSP、ADX、DMP、CDP 等程序化广告模块。

本轮扫描得到三个结论：

1. 业务范围已经接近商业网盟平台的核心闭环，但部分文档把“设计目标、内存适配器、生产能力”混写，容易造成验收误判。
2. `platform-affiliate` 的生产 Repository 已统一为 MyBatis-Plus Mapper。基础设施层继续承载 Click、Conversion、Offer、Goal 等共享 Mapper；无数据库测试仍可使用内存回退。
3. 现有模型已经支持多目标事件和阶梯佣金，但结算入口仍需严格依赖 `APPROVED` 状态、幂等键和账期快照，后续要把“可归因”和“可结算”彻底分开。

## 2. 商业平台对标能力

参考商业网盟产品公开的通用能力，平台应按以下五个工作台组织产品：

| 工作台 | 关键对象 | 必须提供的能力 |
| --- | --- | --- |
| 广告主 | Advertiser、Offer、Goal、Creative | Offer 审核、定向、Cap、阶梯佣金、素材和转化回传配置 |
| 渠道客 | Affiliate、Application、Tracking Link、SmartLink | 入驻/KYC、Offer 申请、链接生成、Sub-ID、收益和付款方式 |
| 追踪归因 | Click Session、Conversion、Attribution | click_id、S2S、去重、归因窗口、跨设备兜底和拒绝原因 |
| 风控合规 | Risk Signal、Blacklist、Review Case | CTIT、IP/UA、异常转化率、人工复核、审计和数据保留 |
| 财务分析 | Payout、Revenue、Invoice、Settlement、Report | 待审核/已批准/拒绝分层、账期、门槛、对账和多维报表 |

成熟产品通常把 Offer 发现、链接生成、实时追踪、反欺诈、对账和付款放在同一操作闭环；差异化重点是数据新鲜度、归因透明度、拒绝原因可解释性和结算准确性，而不是单一接口数量。

## 3. 领域规则

- 一个 Offer 可以有多个 Offer Goal；Goal 的佣金和收入独立于 Offer 默认价格。
- 一个 Click Session 可以产生多个不同 Goal 的 Conversion，但 `(offer_id, goal_id, tx_id)` 必须幂等。
- `PENDING` 只表示已归因，`APPROVED` 才表示可以进入结算；`FRAUD_SUSPECTED` 和 `REJECTED` 不得生成应付分录。
- Cap 扣减必须是原子操作。超过 Cap 的转化保留事实记录并标记拒绝，不得静默丢失。
- Fallback 只在主 Offer 不可用、定向不匹配或 Cap 达到时触发；Fallback 自身也要重新检查状态、定向和 Cap。
- Payout 是渠道应付，Revenue 是平台向广告主确认的收入；报表同时展示两者和毛利。
- 所有追踪、归因、结算和缓存键必须带 tenant_id，跨租户查询默认拒绝。

## 4. 当前已落地

- 修复 `OfferService` 编译断裂，统一 Mapper、Entity 和 domain record 的转换。
- Offer 保存、查询、列表和内存回退可用；支持状态、过期时间、国家/设备定向数据。
- 阶梯佣金支持渠道 ID 和渠道等级覆盖，并在内存模式下按规则替换更新。
- 多目标 Goal 支持新增、查询、列表和软删除，删除后不再参与归因。
- 日转化 Cap 采用并发原子计数；主 Offer 和 Fallback 都进行可用性与 Cap 检查。
- `platform-affiliate` 12 个测试全部通过。

本轮继续完成了联盟扩展与数据层收敛：

- 通知、通知偏好、Webhook、KYC、税务文档、合规违规、支付方式、支付交易、触点、归因结果、商品目录、推荐关系、推荐佣金和效果报表缓存 Repository 已改为 MyBatis-Plus Mapper，服务层原有查询语义保持不变。
- 上述实体补齐 `@TableName`、`@TableId`，数据库生产路径不再依赖 Spring Data 派生查询；无数据库的单元测试仍可使用已有内存适配器。
- CDP `IdentityGraphService` 已接入 `cdp_identity_graph`，按租户写入、查询和删除身份关联；身份映射与身份图谱共用同一张反查表。
- 预算预占增加超时回收任务；本地预算切片预取后立即确认主流水，释放时只回收到本地切片，避免主库预占永久悬挂或重复释放。
- 新增 Flyway `V9__affiliate_extended_persistence.sql`，覆盖联盟扩展表（含 Creative）、预算账户、钱包和基础 API Key 表；同时为旧版通知、Webhook、IP 缓存表补齐实体所需列。`V6` 重复版本已清理，避免 Flyway 启动时因重复版本直接失败。
- 报表 Cohort、日报表、DMP 分群、CDP 时间线和事件 Outbox 已有 MyBatis-Plus 生产读写路径，数据库不可用时才回退内存测试实现。

## 5. 分阶段实现计划

### P0：可运营闭环

- 为 Conversion 增加数据库唯一约束和服务层幂等查询：`tenant_id + offer_id + goal_id + tx_id`。
- 将 Click、Conversion、Goal 的 tenant 校验下沉到统一的 `AffiliateTenantGuard`。
- 结算服务只接收 APPROVED 转化，生成不可变 Billing Entry，并记录 invoice snapshot。
- 增加 Offer/Goal/Partner/Postback 的审计事件和人工审核原因。

### P1：商业化运营

- Offer 申请审批、渠道分组和私有 Offer；支持渠道专属佣金有效期。
- SmartLink 按国家、设备和 7 天 EPC 分桶，增加 ROUND_ROBIN、WEIGHTED 和 GEO_OPTIMIZED 策略。
- 报表增加时区、归因窗口、迟到事件重算和导出任务。
- Postback 增加签名、重试退避、死信和交付状态查询。

### P2：规模化与合规

- Redis Lua 实现 Cap、频控和幂等键，PostgreSQL 分区存储 Click/Conversion 事实。
- Kafka Outbox Relay、对账差异工作流和财务月结锁。
- KYC、税务文档、数据删除/退出、审计留存和租户级数据导出。
- OpenTelemetry、SLO、压测和故障演练；所有性能指标以压测报告为准，不把本地基准当生产承诺。

### 当前收尾项

- 联盟扩展 Repository 已全部移除 Spring Data JPA 依赖；OfferApplication、Creative、IP 地理缓存等扩展表已纳入 MyBatis-Plus 与 Flyway。ClickSession、Conversion、Offer、Goal 继续使用 `platform-infrastructure` 的共享 Mapper，避免重复实体定义。
- 报表服务保留本地缓存仅用于无数据库测试；启用数据库时仍需持续校验 Mapper 查询结果与缓存失效策略。
- 启用 Flyway 前应在目标数据库执行一次备份，并将 `spring.flyway.enabled` 或 `FLYWAY_ENABLED` 设置为 `true`；迁移脚本使用 `IF NOT EXISTS`，适合已有基础表的增量部署。

## 6. 验收指标

验收以固定数据集和压测脚本为准：点击追踪 P95、S2S 归因 P95、Cap 超限准确率、重复 Postback 计费次数、结算对账差异、Postback 成功率和租户越权拦截率。文档中的延迟数字是目标值，必须在 PostgreSQL、Redis、Kafka 和多实例环境中重新测量。
