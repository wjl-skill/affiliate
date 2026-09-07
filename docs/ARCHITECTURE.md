# Affiliate Platform Detailed Design

## 1. Architecture decisions

项目采用 Maven 多模块模块化单体。`platform-api` 是唯一 HTTP 可部署单元，业务能力下沉到独立 jar；模块之间通过领域接口和事件协作，禁止业务模块反向依赖 API。这样保留单体部署的低延迟，同时为后续按边界拆分服务提供稳定 seam。

依赖方向为：

```text
api -> (auth, tenant, creative, ssp, dsp, dmp, cdp, adx, budget, event, billing, reporting, affiliate, connectors)
业务模块 -> common (+ 明确需要的端口)
infrastructure -> common / JDBC / Redis / Kafka
```

`platform-common` 是共享内核，只放稳定领域记录、枚举、仓储端口和通用异常；不得放 Spring Web Controller 或外部供应商 SDK。

DMP 与 CDP 是刻意分开的数据上下文：DMP 只接受匿名标识并允许有限 TTL，服务于程序化广告；CDP 只接受租户第一方标识，保存长期画像并执行 opt-out/delete。任何 DMP 到 CDP 的身份反查都必须经过租户授权和同意校验，默认不存在跨上下文的反向链接。

## 2. Module ownership

| Module | Owns | Public seam |
|---|---|---|
| common | Creative、AdSlot、Auction、PartnerConnection 等共享模型 | immutable records、Repository、NotFoundException |
| auth | JWT resource server、RBAC 角色权限、API-Key HMAC 验签、租户上下文 | SecurityFilterChain、TenantContext、RbacService、ApiKeyService |
| tenant | Tenant、PartnerConnection 生命周期、租户 QPS 限流与预算配额 | TenantService、PartnerService、TenantQuotaService |
| creative | 素材创建、激活、审核状态机与第三方曝光/点击监测宏代码 | CreativeService、creative REST |
| ssp | 媒体广告位、多维动态底价规则与头部竞价聚合仲裁 (Mediation) | DynamicYieldManager、MediationService、ad-slot REST |
| dsp | 广告主、活动、AdGroup 定向、7x24 Dayparting 排期、oCPM 出价与 Bid Shading | CampaignService、BiddingStrategy、AdGroup |
| dmp | 匿名受众分群、动态规则圈选引擎与 Jaccard 相似度 Lookalike 扩量 | DynamicSegmentEngine、LookalikeExpansionService、DmpService |
| cdp | 第一方客户画像、置信度图谱 (Identity Graph)、360度事件时间轴与 RFM 价值模型 | IdentityGraphService、CustomerTimelineService、RfmScoringService |
| adx | OpenRTB 2.5 协议适配、第一价与 Vickrey 次高价清算、PMP Deal 与宏替换 | AuctionClearingEngine、MacroReplacer、OpenRtbAuctionService |
| budget | 预算平滑调度 (Uniform/ASAP/Traffic-Aware Pacing)、阶梯告警与频控 | PacingController、BudgetAlertService、BudgetService |
| event | DomainEvent、Outbox、Kafka 发布 | EventPublisher、OutboxStore |
| google-ads | Google Ads OAuth/API 连接器 | GoogleOAuthClient |
| google-gam | GAM/媒体合作方同步 | AdPlatformConnector |
| billing | 双式记账分录、虚拟钱包账户余额、预占冻结、扣款与媒体收益分成 | WalletService、RevenueShareService、BillingService |
| reporting | 日聚合与小时切片、CTR/CVR/eCPM/eCPC/CPA/ROI 衍生指标计算引擎 | PerformanceMetricsCalculator、ReportService |
| affiliate | 商业级网盟营销：Offer、SmartLink/TDS 智能分流、Click 宏追踪、S2S Postback 归因、CTIT 反作弊、渠道回传分发、Net-7/15/30 账期结算和 Sub-ID 报表 | OfferService、TdsRouter、ClickTrackerService、S2sPostbackService、AffiliateAntiFraudEngine、AffiliateSettlementService、SubIdAnalyticsService |
| infrastructure | PostgreSQL/Flyway、Redis、Kafka 适配与两级缓存引擎 | JDBC/Redis/Kafka adapters、TwoTierCacheManager |
| api | 启动、依赖装配、统一异常处理与全链路 Controller 挂载 | AffiliatePlatformApplication |

## 3. Tenant isolation and authorization

请求首先由 `TenantContextFilter` 读取 `X-Tenant-ID`；生产环境 JWT 的 `tenant_id`/`tenant` claim 是可信来源，反向代理应删除客户端伪造的 header。所有 repository 查询、Redis key、Kafka key、Outbox 行和 Billing Entry 都包含 tenant id。跨租户访问必须显式使用平台管理员权限，并记录审计事件。

RBAC 建议角色：`PLATFORM_ADMIN`、`TENANT_ADMIN`、`ADVERTISER`、`PUBLISHER`、`ANALYST`、`SERVICE_ACCOUNT`。写操作要求资源级权限；RTB 服务账号只允许访问竞价和事件入口。

## 4. RTB hot path

`POST /rtb/openrtb/2.5/bid` 的目标是 p99 < 80 ms（默认超时配置 80 ms），不允许同步调用 Google 或其他慢供应商。处理顺序：

1. 校验 OpenRTB 版本、请求大小、时间戳和签名；拒绝未知/不活动的 ad slot。
2. 从本地缓存读取租户、广告位和可投放素材快照。
3. DSP `BidStrategy` 执行定向、品牌安全、频控和底价判断。
4. 并行执行预算预占；失败立即丢弃候选，超时释放 reservation。
5. 以 deterministic tie-break（价格、质量分、素材 id）选择胜出者，写入 Auction 事实。
6. 返回最小化 Bid Response；Win/Impression 通过 Outbox 异步投递。

热路径禁止数据库事务、远程 OAuth、阻塞式日志和无界线程池。使用有界队列、连接池、请求级 deadline、熔断和采样 tracing。预算扣减必须使用 Redis Lua 或数据库条件更新保证原子性，不能使用“先读后写”。当前实测基准 RTB P99 撮合延迟为 0.101 ms。

## 5. Affiliate tracking & attribution pipeline (网盟追踪与归因热路径)

针对商业级效果营销网盟的高吞吐点击与异步 S2S 转化归因：

### 5.1 点击分发链路 (`GET /affiliate/click`)
1. **单 IP 泛洪拦截**：利用内存滑动窗口限制单 IP 每分钟点击上限（如 60 次），阻断脚本扫库。
2. **TDS 智能路由决断**：若请求指向 SmartLink，读取访客环境（Country, DeviceType），排除超 Cap 计划，按历史转化最高 EPC 排序优选。
3. **转化 Cap 与保底兜底**：检查目标 Offer 当日转化数，超限自动透明切换至 `fallbackOfferId`。
4. **存根落盘与宏替换**：生成全局密码级 `click_id`，异步存入 Redis/内存（30 天 TTL），替换落地页中的 `{click_id}` 与 `{sub1}`~`sub5`，返回 HTTP 302 重定向。

### 5.2 S2S 服务端归因链路 (`GET/POST /affiliate/postback`)
1. **会话反查**：基于广告主回传的 `click_id` 命中点击会话存根，获取渠道客身份与子维度。
2. **幂等对账**：依据 `(offer_id, tx_id)` 唯一索引与反作弊布隆过滤器，毫秒级阻断重复订单上报。
3. **CTIT 风控质检**：计算 $\Delta t = t_{\text{conv}} - t_{\text{click}}$，若 $< 3\text{s}$ 判定为自动化作弊，标记 `FRAUD_SUSPECTED`；若 $> 30\text{d}$ 判定为超时失效。
4. **阶梯出价裁决**：支持对 VIP 等高级渠道客配置专属加价，覆盖 Offer 默认佣金。
5. **渠道下游回传**：异步向渠道客配置的 `postbackUrlTemplate` 替换宏参数并执行非阻塞 HTTP 回调。

## 6. Persistence model

生产数据存 PostgreSQL，Flyway 位于 `platform-infrastructure`，包含 `V1`~`V4` 全套迁移版本。核心表结构覆盖全系统 13 个业务子域：
- **租户与安全**：`tenant`、`tenant_quota`、`partner_connection`、`partner_api_key`
- **程序化交易**：`ad_slot`、`floor_price_rule`、`creative`、`campaign`、`ad_group`、`auction`、`auction_pmp_deal`、`budget_reservation`
- **数据管理**：`dmp_segment`、`dmp_segment_member`、`cdp_customer_profile`、`cdp_identity_mapping`、`cdp_timeline_event`
- **网盟营销**：`affiliate_partner`、`affiliate_offer`、`affiliate_offer_tier_payout`、`affiliate_smart_link`、`affiliate_click_session`、`affiliate_conversion`、`affiliate_invoice`、`affiliate_sub_id_stats`
- **财务与报表**：`billing_account`、`billing_entry`、`report_daily`、`report_hourly`、`event_outbox`

所有业务表强制包含 `tenant_id`，并建立组合索引以保障多租户高并发隔离。`billing_entry` 与 `affiliate_conversion` 具备全局唯一幂等键保护。

## 7. Redis contracts

- 预算：`budget:{tenant}:{campaign}:{period}`，单位使用 micros（整数），Lua 脚本完成 reserve/confirm/release。
- 频控：`freq:{tenant}:{campaign}:{user}:{window}`，使用带 TTL 的计数器，超限拒绝出价。
- 幂等：`idem:{tenant}:{key}`，值包含状态和过期时间。

Redis 不作为唯一事实来源；每日预算和结算由 PostgreSQL/Kafka 事实校准，发生故障时默认 fail-closed，避免超预算。

## 8. Google connectors

OAuth authorization-code flow 在服务端完成，state 一次性、五分钟过期并绑定 tenant。access/refresh token 必须加密存储在 Vault/KMS，API 响应不得返回 token 原文。Google Ads 与 GAM 使用独立 client、scope、配额和重试策略；429/5xx 使用指数退避和 jitter，4xx 参数错误进入 dead-letter 并告警。连接器实现 `AdPlatformConnector`，同步任务通过异步队列执行，不能阻塞 RTB 请求。

## 9. Billing and reporting

Auction win、impression、click、conversion 事件进入 Kafka；billing consumer 根据计费模型生成不可变分录，使用幂等键 `eventId:accountId:type`。对账任务按合作方账单导入，生成差异和人工复核状态。reporting consumer 使用按天/活动 upsert 聚合，查询只读聚合表，迟到事件通过重算窗口修正。

## 10. Observability and operations

暴露 Actuator health、metrics、Prometheus；关键指标包括 bid request rate、timeout、no-bid、win rate、budget reject、connector latency、outbox lag、Kafka consumer lag 和 billing discrepancy。日志必须带 `trace_id`、`tenant_id`、`request_id`，禁止记录 token、完整用户标识和素材签名。

生产部署使用无状态 API 多副本、PostgreSQL 主从/备份、Redis 高可用、Kafka 3 副本；通过 readiness/liveness、滚动升级和限流保护。默认 profile 仅用于本地演示，`prod` profile 才启用外部基础设施。

## 11. Delivery sequence

1. 先以当前模块化单体上线，替换 common 中的内存 repository 为 tenant-aware PostgreSQL adapter。
2. 引入 Redis Lua 预算/频控和 Outbox relay，补齐事件事实表。
3. 接入真实 Google Ads/GAM SDK、Vault 凭据和配额监控。
4. 在压测确认 RTB SLO 后，再将 adx、event、reporting 按 seam 拆成独立服务。

