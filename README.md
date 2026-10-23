# Affiliate Platform

面向程序化广告和网盟业务的 Java 21 / Spring Boot 3.4 多模块平台（共 **20 个 Maven 模块**），覆盖 DSP、SSP、ADX、广告素材与 AI 创意工作台、DMP、CDP、多触点归因、预算、事件、Google 连接器、计费、OLAP 多维报表和统一 AI 推理中枢（`platform-ai`）等边界。

当前交付形态是**模块化单体**：`platform-api` 是可直接部署的 Spring Boot 应用，其余模块以独立 jar 参与构建。模块之间保持明确依赖方向，后续可以按 RTB、事件或报表边界拆分为微服务。

> 当前代码提供可运行的双模（PostgreSQL + 并发内存回退）适配器、真实的 OpenRTB 2.5 / SKAN 4.0 / LinUCB Bandit / AI 编排基础流程，适合功能联调、架构验证与生产演进。生产环境必须启用 PostgreSQL、Redis、Kafka、JWT 和密钥管理。

## 文档入口

### 1. 全局架构与生产升级实施
- **[下一代智能化升级实施规范 NEXT_GEN_UPGRADE_AND_IMPLEMENTATION_SPECIFICATION.md](docs/NEXT_GEN_UPGRADE_AND_IMPLEMENTATION_SPECIFICATION.md)**：LinUCB 上下文多臂老虎机、Apple SKAdNetwork 4.0 隐私归因、自演化 Creative Autonomous Agent、统一 AI 推理中枢与分层 OLAP 数仓实施规范。
- **[生产模块优化与加固设计 PRODUCTION_MODULE_OPTIMIZATION_AND_HARDENING_DESIGN.md](docs/PRODUCTION_MODULE_OPTIMIZATION_AND_HARDENING_DESIGN.md)**：核心模块高并发加固、双模持久化与容灾降级规范。
- **[生产级全链路架构升级与优化实施总报告 PRODUCTION_FULL_STACK_OPTIMIZATION_REPORT.md](docs/PRODUCTION_FULL_STACK_OPTIMIZATION_REPORT.md)**：平台全链路（网盟营销 Phase 1~4、SSP/DSP 倒排与阻尼出价、ADX/CDP 多边撮合与身份图谱闭环）完整实施细节、设计公式与实测质量基准总报告。
- **[详细系统架构设计 ARCHITECTURE.md](docs/ARCHITECTURE.md)**：模块边界、分层依赖拓扑、RTB 纳秒级热路径、多租户行级物理隔离、可靠事件 Outbox 与生产部署约束。
- **[领域词汇规范 CONTEXT.md](CONTEXT.md)**：统一 DMP、CDP、DSP、SSP、ADX、Affiliate 广告网络等核心业务概念与领域语言。

### 2. 生产运维与性能工程
- **[生产部署与运维操作指南 PRODUCTION_DEPLOYMENT_AND_OPERATIONS_GUIDE.md](docs/PRODUCTION_DEPLOYMENT_AND_OPERATIONS_GUIDE.md)**：Kubernetes 云原生编排规范、数据面与控制面物理隔离、Prometheus 监控大盘与零停机滚动发布手册。
- **[端到端性能压测与基准评估报告 PERFORMANCE_BENCHMARK_REPORT.md](docs/PERFORMANCE_BENCHMARK_REPORT.md)**：万级/十万级 QPS 压测方法论、k6 负载脚本与实测 152k QPS / P99 2.67ms 核心基准。

### 3. 算法与安全风控
- **[多触点归因算法与商业级反欺诈指南 ATTRIBUTION_AND_ANTI_FRAUD_GUIDE.md](docs/ATTRIBUTION_AND_ANTI_FRAUD_GUIDE.md)**：Data-Driven MTA 多触点归因、6 大归因模型横向比对、超音速跨国漂移检测与多维反作弊风控引擎。

### 4. 网盟营销 (Affiliate Network) 业务与技术体系
- **[商业需求文档 AFFILIATE_BUSINESS_REQUIREMENTS.md](docs/AFFILIATE_BUSINESS_REQUIREMENTS.md)**：完整的业务功能规格说明，涵盖渠道分级、Offer 配置、点击追踪、转化归因、SmartLink/TDS 智能分流、反欺诈、Sub-ID 多维分析、账期结算和支付网关等业务闭环。
- **[技术设计文档 AFFILIATE_TECHNICAL_DESIGN.md](docs/AFFILIATE_TECHNICAL_DESIGN.md)**：详细的技术架构与实现方案，包括高并发点击追踪（P95<50ms）、S2S 转化回传、Redis 预算 Cap 原子控制、Kafka 异步事件流、CTIT 漏斗检测、SmartLink EPC/LinUCB 路由、SKAN 4.0 归因与分布式账期结算。
- **[API 集成指南 AFFILIATE_API_INTEGRATION_GUIDE.md](docs/AFFILIATE_API_INTEGRATION_GUIDE.md)**：面向广告主与渠道客的完整 API 对接手册，包含点击追踪、S2S Postback 转化上报、Offer 管理、渠道管理、多维报表查询等接口规范及多语言对接示例。

### 5. 素材自动化 (Creative Automation) 生产体系
- **[素材自动化系统详细设计总纲 CREATIVE_AUTOMATION_SYSTEM_DESIGN.md](docs/CREATIVE_AUTOMATION_SYSTEM_DESIGN.md)**：涵盖全链路架构、多模型 AI 视频生成、7 阶段长视频流水线与 6 管线 Prompt、数字人与 TTS 多模态、剪映草稿微秒级导出、7 日动态大盘质量基准线打标、Creative Autonomous Agent 自演化闭环、跨渠道批量建广与算力分账。
- **[素材自动化专题设计文档集 (docs/creative-automation/)](docs/creative-automation/README.md)**：分模块专题深入设计，包含多模型引擎（`01`）、长视频工作台（`02`）、智能混剪（`03`）、质量打标与数仓 ETL（`04`）、自动化建广（`05`）、财务算力分账（`06`）。
- **[素材自动化独立建表脚本](docs/sql/16_platform_creative_automation.sql)**：包含 12 张核心业务表与扩展表的 PostgreSQL 生产 DDL 与高性能复合索引。

### 6. 数据库设计与平滑演进
- **[数据库迁移演进指南 DATABASE_MIGRATION_GUIDE.md](docs/DATABASE_MIGRATION_GUIDE.md)**：灰度双写校验、影子流量回放与自动化对账割接方案。
- **[Flyway 生产自动迁移脚本](platform-infrastructure/src/main/resources/db/migration/V1__platform_schema.sql)**：PostgreSQL/Flyway `V1`~`V19` 版本化增量迁移脚本（共 82 张核心表，与 82 个 MyBatis-Plus Entity 100% 双向对齐）。
- **[独立模块 SQL 脚本规范](docs/sql/13_platform_affiliate.sql)**：包含业务模块独立建表 DDL 及 `all_modules_schema.sql` 一键整合初始化脚本。

## 架构总览

```text
                          ┌──────────────────────┐
                          │     platform-api     │
                          │ Spring Boot / REST  │
                          └──────────┬───────────┘
                                     │
       ┌──────────────┬──────────────┼──────────────┬──────────────┐
       │              │              │              │              │
    Identity       Supply         Demand          Auction      Data & AI
  auth/tenant   creative/ssp    dsp/budget        adx/RTB    dmp/cdp/ai/attr
       │              │              │              │              │
       └──────────────┴──────────────┴──────┬───────┴──────────────┘
                                            │
                       affiliate / event / billing / reporting
                                            │
                          infrastructure adapters
                      PostgreSQL · Redis · Kafka · Flyway
```

依赖原则：`platform-api -> 业务模块 -> platform-common`；基础设施只实现端口，不反向依赖 API。`platform-common` 仅保存稳定领域模型、仓储端口和通用异常。

## 模块与功能 (20 个 Maven 模块)

| 模块 | 当前职责和已实现能力 |
|---|---|
| `platform-common` | 共享领域记录：Creative、AdSlot、Auction、PartnerConnection、枚举、内存仓储和通用异常。 |
| `platform-infrastructure` | MyBatis-Plus 82 个 Entity/Mapper、Flyway `V1`~`V19` 迁移脚本、Redis/Kafka 适配与二级缓存。 |
| `platform-auth` | Spring Security、JWT 鉴权、RBAC 角色权限、动态树形菜单（`sys_menu`）、API-Key 与 `TenantContext`。 |
| `platform-tenant` | 租户生命周期、配额限流；DSP/SSP/ADX 合作方连接配置与状态管理。 |
| `platform-ai` | 统一 AI 推理与大模型编排中枢：OpenAI/DeepSeek/Claude/千问客户端、Prompt 模板引擎、Token 计量与领域语义回退引擎。 |
| `platform-creative` | 素材管理、多模型 AI 视频生成、长视频分镜 Studio、CapCut 草稿导出、7 日质量打标与 Creative Autonomous Agent 自演化闭环。 |
| `platform-ssp` | 媒体广告位、多维动态底价规则与头部竞价聚合仲裁（Mediation）。 |
| `platform-dsp` | Campaign/AdGroup 定向、7x24 Dayparting、oCPM 智能出价、Bid Shading 与 AI 竞价诊断建议。 |
| `platform-dmp` | 匿名 Cookie/设备 ID 受众分群、动态规则圈选、TTL 过期判断和 Jaccard Lookalike 扩量。 |
| `platform-cdp` | 第一方实名身份图谱（Identity Graph）、客户画像合并、360 度事件时间轴与 RFM 分层。 |
| `platform-attribution` | 多触点归因引擎（First/Last/Linear/Time-Decay/U-Shaped/Data-Driven MTA）与 6 模型横向比对。 |
| `platform-adx` | OpenRTB 2.5 协议解析、第一价/Vickrey 次高价清算、PMP Deal 优先交易、GDPR/TCF 合规与竞价漏斗遥测。 |
| `platform-budget` | `reserve/confirm/release` 预算端口、平滑 Pacing 控制器、本地 CAS 与 Redis Lua 原子适配器。 |
| `platform-event` | DomainEvent、内存 Outbox、PostgreSQL Outbox 与 Kafka 可靠发布器。 |
| `platform-google-ads` | Google OAuth authorization-code 流程，服务端交换 code 与状态校验。 |
| `platform-google-gam` | `AdPlatformConnector` 抽象和 GAM 同步连接器实现。 |
| `platform-billing` | 双式记账分录、虚拟钱包余额预占扣款、媒体分成结算与批量打款（Payout Batch）。 |
| `platform-reporting` | 日/小时聚合报表、分层 OLAP 星型立方体（`OlapReportingEngine`）、Cohort 留存 LTV 与 AI 高管商业洞察。 |
| `platform-affiliate` | 商业级网盟核心：Offer、SmartLink/TDS（EPC + LinUCB Bandit）、SKAN 4.0 隐私归因、S2S Postback、4D 反作弊 + AI 归因诊断、Net-7/15/30 结算与 Sub-ID 报表。 |
| `platform-sdk` | 广告主与媒体端事件上报客户端 SDK 封装。 |
| `platform-api` | 应用启动、模块组合、统一异常响应、健康探针和打包入口。 |

## 数据库架构与 SQL 目录索引

平台全面支持 PostgreSQL 14+，提供完备的 DDL、强类型字段、中文注释、索引与外键约束：

- **Flyway 生产自动迁移脚本**（`platform-infrastructure/src/main/resources/db/migration/`）：
  - `V1`~`V4`：基础平台骨架表、广告实体、拍卖连接与网盟营销核心表
  - `V5`~`V13`：网盟进阶阶梯佣金、RBAC 与系统安全表、系统配置字典、DSP/ADX/DMP/CDP/Attribution/Billing/Reporting 生产级扩展表
  - `V16`~`V17`：AI 素材自动化 12 张核心表（ModelMeta、TaskRecord、BillingLedger、StudioProject/Scene、Benchmark7D 等）与外键加固
  - `V18`：下一代智能化升级表（`affiliate_skan_postback`、`affiliate_bandit_arm`、`creative_agent_action`、`report_hourly_olap_cube`）
  - `V19`：AI 大模型配置、Prompt 模板库与调用计量审计表（`ai_model_config`、`ai_prompt_template`、`ai_usage_log`）
- **双向 Schema 审计工具**：
  - 执行 `node scripts/audit-entity-schema.mjs` 可自动校验 82 个 MyBatis-Plus Entity 与 82 张 Flyway 表结构的双向 100% 匹配。

## REST API 概览

```text
POST/GET /api/v1/tenants
POST/GET /api/v1/partners
POST/GET /api/v1/creatives
POST/GET /api/v1/creative/studio/projects
POST     /api/v1/creative/agent/refine
GET      /api/v1/creative/agent/actions
POST/GET /api/v1/ai/configs
POST/GET /api/v1/ai/templates
POST     /api/v1/ai/chat
GET      /api/v1/ai/stats
GET      /api/v1/ai/logs
POST/GET /api/v1/ad-slots
POST/GET /api/v1/campaigns
POST/GET /api/v1/dmp/segments
POST/GET /api/v1/cdp/profiles
POST/GET /api/v1/billing/entries
GET      /api/v1/reports/daily
GET      /api/v1/reports/olap
GET      /api/v1/reports/ai-insights
POST     /api/v1/affiliate/skan/postback
GET      /api/v1/affiliate/skan/stats/{campaignId}
GET      /api/v1/affiliate/anti-fraud/ai-investigate/{clickId}
POST     /rtb/openrtb/2.5/bid
```

## 验证与测试

执行双向 Entity-Schema 对齐检查：

```bash
node scripts/audit-entity-schema.mjs
```

执行全工程 20 个 Maven 子模块的全量测试与构建验证：

```bash
mvn clean test
```
