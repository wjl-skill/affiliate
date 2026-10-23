# Affiliate Platform Detailed Design

## 1. Architecture decisions

项目采用 Maven 多模块模块化单体（共 20 个子模块）。`platform-api` 是唯一 HTTP 可部署单元，业务能力下沉到独立 jar；模块之间通过领域接口和事件协作，禁止业务模块反向依赖 API。这样保留单体部署的低延迟，同时为后续按边界拆分服务提供稳定 seam。

依赖方向为：

```text
api -> (auth, tenant, ai, creative, ssp, dsp, dmp, cdp, attribution, adx, budget, event, billing, reporting, affiliate, sdk, connectors)
业务模块 -> common (+ infrastructure / ai 明确需要的端口)
infrastructure -> common / MyBatis-Plus / PostgreSQL / Redis / Kafka
```

`platform-common` 是共享内核，只放稳定领域记录、枚举、仓储端口和通用异常；不得放 Spring Web Controller 或外部供应商 SDK。

DMP 与 CDP 是刻意分开的数据上下文：DMP 只接受匿名标识并允许有限 TTL，服务于程序化广告；CDP 只接受租户第一方标识，保存长期画像并执行 opt-out/delete。任何 DMP 到 CDP 的身份反查都必须经过租户授权和同意校验，默认不存在跨上下文的反向链接。

## 2. Module ownership (20 Modules)

| Module | Owns | Public seam |
|---|---|---|
| common | Creative、AdSlot、Auction、PartnerConnection 等共享模型 | immutable records、Repository、NotFoundException |
| infrastructure | PostgreSQL/Flyway (`V1`~`V19` 共 82 张表)、MyBatis-Plus 82 个 Entity/Mapper、Redis、Kafka 适配与两级缓存 | MyBatis-Plus Mappers、Redis/Kafka adapters、TwoTierCacheManager |
| auth | JWT resource server、RBAC 角色权限、动态树形菜单 (`sys_menu`)、API-Key HMAC 验签、租户上下文 | SecurityFilterChain、TenantContext、RbacService、MenuManagementService |
| tenant | Tenant、PartnerConnection 生命周期、租户 QPS 限流与预算配额 | TenantService、PartnerService、TenantQuotaService |
| ai | 多租户 AI 模型配置、结构化 Prompt 模板渲染、Token 计量审计、跨模块智能编排与领域语义回退引擎 | AiOrchestratorService、AiModelConfigService、AiPromptTemplateService、AiUsageMeteringService |
| creative | 素材管理、多模型 AI 视频生成、长视频分镜 Studio、CapCut 导出、7 日大盘质量打标与 Creative Autonomous Agent | CreativeService、CreativeStudioService、CreativeAutonomousAgentService、AdLearningRuleEngine |
| ssp | 媒体广告位、多维动态底价规则与头部竞价聚合仲裁 (Mediation) | DynamicYieldManager、MediationService、ad-slot REST |
| dsp | 广告主、活动、AdGroup 定向、7x24 Dayparting 排期、oCPM 出价、Bid Shading 与 AI 竞价策略咨询 | CampaignService、BiddingStrategy、AdGroup |
| dmp | 匿名受众分群、动态规则圈选引擎与 Jaccard 相似度 Lookalike 扩量 | DynamicSegmentEngine、LookalikeExpansionService、DmpService |
| cdp | 第一方客户画像、置信度图谱 (Identity Graph)、360度事件时间轴与 RFM 价值模型 | IdentityGraphService、CustomerTimelineService、RfmScoringService |
| attribution | 6 大多触点归因模型 (First/Last/Linear/Time-Decay/U-Shaped/Data-Driven MTA) 与横向比对 | AttributionEngine、AttributionService |
| adx | OpenRTB 2.5 协议适配、第一价与 Vickrey 次高价清算、PMP Deal 与宏替换 | AuctionClearingEngine、MacroReplacer、OpenRtbAuctionService |
| budget | 预算平滑调度 (Uniform/ASAP/Traffic-Aware Pacing)、阶梯告警与频控 | PacingController、BudgetAlertService、BudgetService |
| event | DomainEvent、Outbox、Kafka 发布 | EventPublisher、OutboxStore |
| google-ads | Google Ads OAuth/API 连接器 | GoogleOAuthClient |
| google-gam | GAM/媒体合作方同步 | AdPlatformConnector |
| billing | 双式记账分录、虚拟钱包账户余额、预占冻结、扣款、媒体分成与批量打款批次 | WalletService、RevenueShareService、BillingService |
| reporting | 日聚合、分层 OLAP 星型立方体 (`OlapReportingEngine`)、Cohort 留存 LTV 与 AI 商业洞察总结 | OlapReportingEngine、CohortAnalysisService、ReportService |
| affiliate | 商业级网盟营销：Offer、SmartLink/TDS (EPC + LinUCB Bandit)、SKAN 4.0 隐私归因、Click 追踪、S2S Postback、4D 反作弊 + AI 诊断、Net-7/15/30 结算和 Sub-ID 报表 | OfferService、TdsRouter、LinUcbBanditEngine、SkanAttributionEngine、ClickTrackerService、S2sPostbackService、AffiliateAntiFraudEngine |
| sdk | 广告主与媒体客户端事件上报 SDK | AffiliateTrackerClient |
| api | 启动、依赖装配、统一异常处理与全链路 Controller 挂载 | AffiliatePlatformApplication |

## 3. Tenant isolation and authorization

请求首先由 `TenantContextFilter` 读取 `X-Tenant-ID`；生产环境 JWT 的 `tenant_id`/`tenant` claim 是可信来源，反向代理应删除客户端伪造的 header。所有 repository 查询、Redis key、Kafka key、Outbox 行和 Billing Entry 都包含 tenant id。跨租户访问必须显式使用平台管理员权限，并记录审计事件。

## 4. RTB hot path

`POST /rtb/openrtb/2.5/bid` 的目标是 p99 < 80 ms，不允许同步调用 Google 或其他慢供应商。倒排索引采用 `AtomicReference<IndexSnapshot>` 无锁原子快照，预算扣减使用 Redis Lua 或 CAS 保证原子性。当前实测基准 RTB P99 撮合延迟 < 0.2 ms。

## 5. Affiliate tracking, Bandit routing & SKAN 4.0 pipeline

1. **点击分发与 LinUCB 智能分流 (`GET /affiliate/click`)**：支持传统 EPC 排序与 `LinUcbBanditEngine` 上下文多臂老虎机在线探索/利用路由，转化回传实时反哺 Bandit 收益矩阵。
2. **S2S 服务端归因 (`GET/POST /affiliate/postback`)**：基于 `click_id` 反查会话，`(offer_id, tx_id)` 唯一幂等保护，4D 反作弊引擎结合 `AiOrchestratorService` 提供自然语言欺诈定性与拦截建议。
3. **Apple SKAdNetwork 4.0 隐私归因 (`POST /api/v1/affiliate/skan/postback`)**：零设备 ID 环境下解析 Fine Conversion Value (0–63) 与 Coarse Value (`low`/`medium`/`high`)，支持双模持久化（`affiliate_skan_postback`）与 Sub-ID 虚拟聚合。

## 6. Persistence model

生产数据存 PostgreSQL，Flyway 位于 `platform-infrastructure`，包含 `V1`~`V19` 全套迁移版本（共 82 张表，与 82 个 MyBatis-Plus Entity 100% 双向映射）：
- **租户、安全与配置**：`tenant`、`sys_user`、`sys_role`、`sys_menu`、`sys_permission`、`sys_config`、`api_key`、`audit_event`
- **程序化交易与数据**：`ad_slot`、`creative`、`campaign`、`dsp_ad_group`、`dsp_bid_strategy`、`adx_deal`、`auction`、`budget_reservation`、`dmp_segment`、`cdp_profile`、`attribution_touchpoint`
- **网盟营销与下一代归因**：`affiliate_partner`、`affiliate_offer`、`affiliate_smart_link`、`affiliate_click_session`、`affiliate_conversion`、`affiliate_skan_postback`、`affiliate_bandit_arm`
- **AI 与素材自动化**：`ai_model_config`、`ai_prompt_template`、`ai_usage_log`、`creative_material`、`creative_studio_project`、`creative_studio_scene`、`creative_agent_action`、`creative_ad_learning_rule`
- **财务与分层数仓**：`wallet_account`、`billing_entry`、`billing_payout_batch`、`report_daily`、`report_hourly_olap_cube`、`report_cohort_acquisition`、`outbox_event`
