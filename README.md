# Affiliate Platform 广告营销与程序化广告一体化中枢

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Nuxt](https://img.shields.io/badge/Nuxt-3.15.4-00DC82.svg)](https://nuxt.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+-blue.svg)](https://www.postgresql.org/)
[![ClickHouse](https://img.shields.io/badge/ClickHouse-24+-yellow.svg)](https://clickhouse.com/)
[![Kafka](https://img.shields.io/badge/Kafka-KRaft_Batch_Consumer-black.svg)](https://kafka.apache.org/)
[![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.9-green.svg)](https://baomidou.com/)
[![Flyway](https://img.shields.io/badge/Flyway-V1~V22_(88_Tables)-red.svg)](https://flywaydb.org/)
[![Schema Alignment](https://img.shields.io/badge/Schema%20Alignment-100%25_(88_Entities)-success.svg)](scripts/audit-entity-schema.mjs)
[![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-Tracing_&_APM-blueviolet.svg)](https://opentelemetry.io/)

面向高并发程序化广告（DSP/SSP/ADX）与商业级网盟营销（Affiliate Network）的一体化数字营销云原生中枢，采用 **Java 21 Project Loom 虚拟线程 + Spring Boot 3.4 模块化单体架构**，由 **19 个 Java Maven 模块** 与 **1 个 Nuxt 3 现代化管理前端** 构成。

系统完整涵盖：OpenRTB 2.5 纳秒级撮合、动态出价（oCPM/Bid Shading）、多触点归因（Data-Driven MTA）、LinUCB 上下文多臂老虎机分流、Apple SKAdNetwork 4.0 隐私归因、4D 实时反欺诈风控、**Offer 存储桶 Parquet 画像补量与 AppsFlyer/Adjust MMP 归因推送**、AI 素材自动化（多模型视频生成/7阶段 Studio/CapCut 草稿导出/7日基准打标）、统一 AI 推理中枢、双式记账资金管理及分层 OLAP 实时星型立方体。

---

## 📚 文档全景导航

完整文档体系收录于 **[`docs/`](docs/README.md)** 知识中枢目录，现存 25 篇权威技术文档与设计规范：

```
docs/
├── README.md                                                        # [全局文档索引中枢与导航地图]
├── ARCHITECTURE.md                                                  # 系统分层架构与模块 Seam 隔离设计
├── NEXT_GEN_UPGRADE_AND_IMPLEMENTATION_SPECIFICATION.md             # 下一代进阶算法(LinUCB/SKAN/Agent)实施规范
├── EVOLUTION_ROADMAP_MILESTONES_IMPLEMENTATION_REPORT.md            # [最新] 8周四大里程碑架构演进落地与验收总报告
├── PRODUCTION_MODULE_OPTIMIZATION_AND_HARDENING_DESIGN.md          # 全模块深度优化、10大漏洞修复与加固规范
├── PRODUCTION_FULL_STACK_OPTIMIZATION_REPORT.md                     # 生产级全链路架构升级与实测基准总报告
├── PRODUCTION_DEPLOYMENT_AND_OPERATIONS_GUIDE.md                    # Kubernetes/Docker 云原生编排与双面运维
├── PERFORMANCE_BENCHMARK_REPORT.md                                  # 端到端万级 QPS 压测方法论与实测 152k 基准
├── ATTRIBUTION_AND_ANTI_FRAUD_GUIDE.md                              # 6大多触点归因算法与商业级 4D 反欺诈指南
├── AFFILIATE_BUSINESS_REQUIREMENTS.md                               # 网盟商业需求规范 (BRD)
├── AFFILIATE_TECHNICAL_DESIGN.md                                    # 网盟核心技术架构与链路设计 (TDD)
├── AFFILIATE_API_INTEGRATION_GUIDE.md                               # 广告主与渠道客 API 对接集成指南
├── DATABASE_MIGRATION_GUIDE.md                                      # 数据库增量演进与多级缓存指南
├── ENTITY_SCHEMA_ALIGNMENT.md                                       # 84 表实体双向对齐规约与审计工具
├── FRONTEND_PRODUCTION_GUIDE.md                                     # Vue 3 / Nuxt 3 管理后台开发规范
├── CREATIVE_AUTOMATION_SYSTEM_DESIGN.md                             # 素材自动化系统架构设计总纲
└── creative-automation/                                             # 素材自动化 6 大专题设计与完备性审计
    ├── README.md                                                    # 素材自动化专题文档总览
    ├── 01-AI_VIDEO_MULTI_MODEL_ENGINE.md                            # 多模型 AI 视频生成引擎规范
    ├── 02-LONG_VIDEO_STUDIO_PIPELINE.md                             # 长视频工作台与 6 管线 Prompt 设计
    ├── 03-VIDEO_PROCESSING_AND_AUTO_STITCH.md                       # 智能混剪、自适应裁切与字幕合成
    ├── 04-MATERIAL_QUALITY_TAGGING_AND_BENCHMARK.md                 # 7日大盘基准打标与数仓 ETL 规范
    ├── 05-AD_AUTOMATION_AND_LEARNING_PHASE.md                       # 跨媒体自动建广与学习期监控
    ├── 06-AI_BILLING_AND_SETTLEMENT.md                              # 算力计费两阶段管控与不可变记账
    └── CREATIVE_AUTOMATION_BOUNDARY_AND_COMPLETENESS_AUDIT.md       # 素材自动化代码完备性与边界审查报告
```

---

## 🏛️ 总体架构拓扑

```
                            ┌────────────────────────┐
                            │    platform-frontend   │
                            │ Nuxt 3 / Vue 3 / TW 23P│
                            └───────────┬────────────┘
                                        │ (REST / JSON)
                            ┌───────────┴────────────┐
                            │      platform-api      │
                            │ Spring Boot 3.4 启动入口 │
                            └───────────┬────────────┘
                                        │
    ┌──────────────┬──────────────┬─────┴────────┬──────────────┬──────────────┐
    │ Identity     │ Supply       │ Demand       │ Auction      │ Growth & Mkt │ Data & AI
 auth / tenant  creative / ssp   dsp / budget   adx / RTB      affiliate      cdp / ai / attr
    │              │              │              │              │              │
    └──────────────┴──────────────┴──────┬───────┴──────────────┴──────────────┘
                                         │
                               event / billing / reporting
                                         │
                            ┌────────────┴───────────┐
                            │ platform-infrastructure│
                            │ MyBatis-Plus 88 Mappers│
                            │ Flyway V1~V22 (88 表)  │
                            └────────────┬───────────┘
                                         │
                  PostgreSQL 16 · ClickHouse 24+ · Redis 7 · Kafka · S3/Parquet
```

**依赖架构规约**：
1. **单向分层依赖**：`platform-api -> 业务模块 -> platform-common`。业务模块之间通过领域接口与事件协作，禁止业务模块反向依赖 `platform-api`。
2. **纯粹共享内核**：`platform-common` 仅保存不可变领域 Record、基础仓储契约与通用异常，严禁引入 Spring Web 或三方 SDK。
3. **数据面与控制面分离**：RTB 高速竞价链路（Dataplane）零慢依赖、单请求耗时 P99 < 0.2ms；复杂统计/AI 生成/计费结算位于控制面（Controlplane）。

---

## 📦 模块职责与能力矩阵 (19 Java + 1 前端)

| 模块名 | 源码行数 / 类数 | 核心职责与业务能力 | 核心 Controller / Seam |
|---|---|---|---|
| **`platform-affiliate`** | 17,920 行 / 94 类 | **商业级网盟营销核心**：Offer 计划管理、SmartLink/TDS（EPC + 12维 LinUCB 多臂老虎机与 A/B 测试对比）、SKAN 4.0 隐私归因、Kafka KRaft 异步转化批处理、4D 动态反作弊（Redis Pub/Sub 广播热生效）+ AI 异常诊断、Net-7/15/30 周期结算。<br>⭐ **新增流量补量引擎**：S3/多云存储桶 Parquet 列式画像解析、URL 宏全量补齐、AppsFlyer/Adjust 真实设备环境 HTTP 推送、Redisson 分布式锁加固。 | `AffiliateClickController`<br>`S2sPostbackController`<br>`SkanPostbackController`<br>`OfferSupplementController`<br>`AntiFraudConfigController`<br>`AffiliateAdminController` |
| **`platform-creative`** | 10,089 行 / 140 类 | **素材自动化生产中枢**：多模型 AI 视频生成、7 阶段长视频分镜 Studio、CapCut 剪映工程草稿微秒级导出（`draft_content.json`）、数字人多模态口播、7 日大盘动态基准打标、自演化 Creative Autonomous Agent、广告学习期监控。 | `AiVideoGenerateController`<br>`StudioProjectController`<br>`VideoProcessController`<br>`MaterialQualityController`<br>`CreativeAutonomousAgentController`<br>`CreativeAdLearningController` |
| **`platform-infrastructure`**| 6,105 行 / 146 类 | **企业级基础设施与持久层**：88 个 MyBatis-Plus Entity 与 Mapper、Flyway `V1`~`V22` 增量迁移脚本（88 张表，含 PostgreSQL 按月范围分区与冷数据归档表）、Redisson 分布式锁、Kafka 消息总线适配与 Caffeine 本地两级缓存。 | `ClickSessionPartitionedMapper`<br>`OfferSupplementLogPartitionedMapper`<br>`TwoTierCacheManager`<br>`RedisLockClient` |
| **`platform-auth`** | 2,174 行 / 19 类 | **统一安全与 RBAC 权限中心**：Spring Security 6、无状态 JWT 验签、多租户上下文绑定（`TenantContext`）、API-Key HMAC 鉴权、动态多级树形菜单（`sys_menu`）与权限管理。 | `AuthController`<br>`SystemSecurityController` |
| **`platform-dsp`** | 1,713 行 / 14 类 | **需求方广告投放系统**：Campaign/AdGroup 排期定向、7x24 Dayparting、oCPM 智能出价、Bid Shading 阻尼衰减与 AI 智能竞价诊断。 | `CampaignController` |
| **`platform-budget`** | 1,515 行 / 12 类 | **预算控制与配额调度**：`reserve/confirm/release` 预算三阶段原子预占、平滑 Pacing 算法（Uniform/ASAP/Traffic-Aware）与本地 CAS / Redis Lua 双模适配。 | `BudgetController`<br>`AdaptivePidPacingController` |
| **`platform-ai`** | 1,494 行 / 16 类 | **统一 AI 认知与大模型网关**：多租户 API Key/Endpoint 动态路由（OpenAI/Claude/DeepSeek/千问）、Prompt 模板引擎、Token 消耗双向计量审计与领域语义回退引擎。 | `AiGatewayController`<br>`AiModelConfigController`<br>`AiPromptTemplateController` |
| **`platform-billing`** | 1,423 行 / 9 类 | **财务清算与双式记账**：不可变借贷分录、虚拟钱包账户余额、预占冻结、自动扣款、媒体分成（Revenue Share）与批量付款批次（Payout Batch）。 | `BillingController` |
| **`platform-cdp`** | 1,394 行 / 10 类 | **客户数据平台**：第一方实名身份图谱（Identity Graph）、置信度映射合并、360 度事件全景时间轴与 RFM 客户价值模型。 | `CdpController` |
| **`platform-adx`** | 1,882 行 / 16 类 | **广告交易撮合引擎**：OpenRTB 2.5 协议解析、第一价与 Vickrey 次高价清算、PMP Deal 优先交易、GDPR/TCF 2.2 合规检查与竞价漏斗遥测。 | `RtbController`<br>`PmpDealController` |
| **`platform-reporting`** | 1,480 行 / 10 类 | **多维数据分析与商业智能**：日/小时多维聚合报表、ClickHouse 原生 HTTP JSONEachRow 向量化 OLAP 查询引擎、分层本地星型立方体透明降级、Cohort 留存与 LTV 分析、AI 高管商业洞察。 | `ReportController` |
| **`platform-tenant`** | 1,082 行 / 12 类 | **多租户治理与配置中心**：多租户生命周期管理、合作方连接生命周期、租户配额限流、多云 S3 对象存储配置管理（AWS/Cloudflare R2/MinIO/OSS）。 | `TenantController`<br>`SystemConfigController` |
| **`platform-common`** | 1,019 行 / 15 类 | **共享内核**：Creative、AdSlot、Auction、PartnerConnection 等共享模型、领域枚举、通用领域异常与仓储抽象端口。 | 共享领域模型 Record |
| **`platform-ssp`** | 749 行 / 6 类 | **供给方媒体变现系统**：广告位管理、多维动态底价规则与多渠道头部竞价聚合仲裁（Mediation）。 | `AdSlotController` |
| **`platform-google-ads`** | 698 行 / 5 类 | **Google Ads 连接器**：OAuth 2.0 授权码交换、状态校验与广告计划批量同步。 | `GoogleOAuthController`<br>`GoogleAdsSyncController` |
| **`platform-dmp`** | 514 行 / 6 类 | **数据管理平台**：匿名 Cookie/设备 ID 受众分群、动态规则圈选引擎与 Jaccard 相似度 Lookalike 人群扩量。 | `DmpController` |
| **`platform-event`** | 439 行 / 10 类 | **可靠事件总线**：DomainEvent 模型、内存发件箱、PostgreSQL 本地事务 Outbox 与 Kafka 异步消息发布。 | `EventPublisher` |
| **`platform-google-gam`** | 221 行 / 3 类 | **Google Ad Manager 连接器**：媒体侧订单、广告单元与投放同步适配。 | `PartnerController` |
| **`platform-api`** | 144 行 / 3 类 | **应用启动与聚合部署入口**：Spring Boot 启动类、全局统一异常处理与探针入口。 | `AffiliatePlatformApplication` |
| **`platform-frontend`** | 7,781 行 / 39 文件 | **现代化管理控制台**：基于 Nuxt 3 + Vue 3 + TailwindCSS 开发，涵盖 **23 个业务页面**（含登录、网盟 Offer/转化/补量抽屉/防刷、素材 Studio、财务打款、系统安全 RBAC、多租户切换等）。 | 23 个 Nuxt 页面路由 |

---

## ⚡ 核心业务与创新技术亮点

### 1. Offer 流量补量与多云 Parquet 画像解析引擎 (`OfferSupplementService`)
- **多云存储桶原生解码**：支持通过 `S3StorageConfig` 从 AWS S3、Cloudflare R2、MinIO、阿里云 OSS 或本地路径读取 Spark 生成的 `.snappy.parquet` 用户画像文件（内置纯 Java Thrift Compact 页头解析器 + Snappy 高性能解压器 + RLE/Bit-Packed 字典解码器，彻底摆脱 Windows 环境下的 Hadoop JNI 冲突）。
- **点击追踪与归因闭环**：每条补量设备记录原子写入 `affiliate_click_session`（生成唯一 `click_id`），支持 Sub-ID 级点击统计累加，确保后续 S2S Postback 归因无缝闭环。
- **MMP 归因平台协议自适配**：针对 AppsFlyer、Adjust、Singular、Branch 自动补齐缺失参数（`pid`, `clickid`, `af_siteid`, `idfa`, `gps_adid`, `s2s=1` 等），完成 URL 宏全量替换，并通过带真实设备上下文（User-Agent, X-Forwarded-For）的非重定向 HTTP 请求推送到归因端。
- **平滑调度与断点续传**：支持单批即时触发与基于游标偏移量（`last_cursor_offset`）的后台定时平滑限流补量调度。

### 2. 纳秒级 RTB 撮合与动态出价
- **无锁快照索引**：基于 `AtomicReference<IndexSnapshot>` 维护倒排定向索引，竞价匹配逻辑零同步锁，实测 RTB P99 撮合延迟 < 0.2ms。
- **oCPM 动态出价与阻尼衰减**：基于转化率预估与 Bid Shading 阻尼衰减公式动态计算最优出价，兼顾胜出率与广告主 ROI。

### 3. 下一代归因算法与实时反欺诈
- **LinUCB 上下文多臂老虎机**：SmartLink/TDS 路由引入在线探索与利用平衡，动态最大化 EPC 收益。
- **Apple SKAdNetwork 4.0 隐私归因**：零设备 ID 环境下解析 Fine Conversion Value (0–63) 与 Coarse Value，支持分层虚拟聚合。
- **4D 反作弊风控引擎**：实时拦截 CTIT 异常（点击农场/点击注入）、超音速跨国漂移、设备/系统突变、数据中心爬虫 IP，结合 `platform-ai` 大模型进行自然语言欺诈定性。

### 4. 素材自动化生产体系 (Creative Automation)
- **多模型并发生成**：抽象统一视频生成协议，对接外部大模型 API，支持 Java 21 虚拟线程长轮询与故障熔断。
- **长视频 Studio 与 CapCut 导出**：支持分镜剧本拆解、首尾关键帧过渡与标准剪映草稿协议（`draft_content.json`）微秒级导出。
- **7 日动态大盘质量打标**：定时 ETL 计算 `creative_benchmark_7d`，自动筛选优质素材并联动 DSP/SmartLink 优先分流。

### 5. 8 周四大里程碑全链路架构演进亮点
- **P0 稳定性加固与 Redisson 分布式锁**：彻底清理全工程 129 处 `catch (Exception ignored)`，消灭所有单机内存状态回退；实装 Redisson 分布式锁对 Offer 补量调度、AI 轮询与视频混剪施加租约保护，杜绝集群重复执行。
- **Kafka KRaft 异步事件消费管线**：转化回传全面接入 `affiliate.events.conversion` 主题，采用 Spring Kafka 批量消费并结合 Redis 实现 24 小时原子去重幂等屏障，自动更新 Sub-ID 级报表统计与对账。
- **PostgreSQL 范围分区表与定期冷数据归档**：Flyway V22 落地 `affiliate_click_session_partitioned` 与 `affiliate_offer_supplement_log_partitioned` 范围分区表（按月 RANGE 分区，覆盖 2026 全年），内置 `create_monthly_partition` 自动预建过程与 `archive_partitioned_records` 自动化 90 天冷数据归档存储过程。
- **4D 反作弊动态风控与 Redis Pub/Sub 秒级热同步**：运营人员可动态调节 CTIT 漏斗范围、速率限制与数据中心 IP 策略，通过 Redis Pub/Sub 广播实现集群全节点秒级热生效；配套风控拦截遥测 API 与 IP/Sub-ID 动态拉黑。
- **12 维特征向量 LinUCB Bandit 与科学 A/B 报表**：分流特征空间扩充至 12 维（涵盖偏置、设备、24h正余弦、国家、Sub-1、先验EPC、VIP、OS、网络、星期周期与转化倾向），提供全自动 A/B 实验对比报表（转化率提升、EPC 提升、95% 置信区间与胜出决策）。
- **ClickHouse 向量化多维 OLAP 报表引擎**：采用原生 HTTP `JSONEachRow` 协议向 ClickHouse 下推星型聚合计算，动态派生 CTR/CVR/EPC/Profit 指标，并与本地内存立方体实现无缝透明降级。
- **OpenTelemetry APM 与 3 套生产级 Grafana 看板**：产出应用链路监控（`apm-tracing-dashboard.json`）、4D 风控拦截（`antifraud-risk-dashboard.json`）与 Offer 补量流量（`supplement-traffic-dashboard.json`）三套开箱即用 Grafana 仪表盘。
- **K8s 数据面与控制面物理容器镜像分离**：基于 `Dockerfile.dataplane` 构建 Generational ZGC 纳秒级镜像（专承接 RTB 竞价与点击跳转），基于 `Dockerfile.controlplane` 构建 G1GC 吞吐量镜像（专承接后台批处理与管理分析）。

### 5. 8 周四大里程碑全链路架构演进亮点
- **P0 稳定性加固与 Redisson 分布式锁**：彻底清理全工程 129 处 `catch (Exception ignored)`，消灭所有单机内存状态回退；实装 Redisson 分布式锁对 Offer 补量调度、AI 轮询与视频混剪施加租约保护，杜绝集群重复执行。
- **Kafka KRaft 异步事件消费管线**：转化回传全面接入 `affiliate.events.conversion` 主题，采用 Spring Kafka 批量消费并结合 Redis 实现 24 小时原子去重幂等屏障，自动更新 Sub-ID 级报表统计与对账。
- **PostgreSQL 范围分区表与定期冷数据归档**：Flyway V22 落地 `affiliate_click_session_partitioned` 与 `affiliate_offer_supplement_log_partitioned` 范围分区表（按月 RANGE 分区，覆盖 2026 全年），内置 `create_monthly_partition` 自动预建过程与 `archive_partitioned_records` 自动化 90 天冷数据归档存储过程。
- **4D 反作弊动态风控与 Redis Pub/Sub 秒级热同步**：运营人员可动态调节 CTIT 漏斗范围、速率限制与数据中心 IP 策略，通过 Redis Pub/Sub 广播实现集群全节点秒级热生效；配套风控拦截遥测 API 与 IP/Sub-ID 动态拉黑。
- **12 维特征向量 LinUCB Bandit 与科学 A/B 报表**：分流特征空间扩充至 12 维（涵盖偏置、设备、24h正余弦、国家、Sub-1、先验EPC、VIP、OS、网络、星期周期与转化倾向），提供全自动 A/B 实验对比报表（转化率提升、EPC 提升、95% 置信区间与胜出决策）。
- **ClickHouse 向量化多维 OLAP 报表引擎**：采用原生 HTTP `JSONEachRow` 协议向 ClickHouse 下推星型聚合计算，动态派生 CTR/CVR/EPC/Profit 指标，并与本地内存立方体实现无缝透明降级。
- **OpenTelemetry APM 与 3 套生产级 Grafana 看板**：产出应用链路监控（`apm-tracing-dashboard.json`）、4D 风控拦截（`antifraud-risk-dashboard.json`）与 Offer 补量流量（`supplement-traffic-dashboard.json`）三套开箱即用 Grafana 仪表盘。
- **K8s 数据面与控制面物理容器镜像分离**：基于 `Dockerfile.dataplane` 构建 Generational ZGC 纳秒级镜像（专承接 RTB 竞价与点击跳转），基于 `Dockerfile.controlplane` 构建 G1GC 吞吐量镜像（专承接后台批处理与管理分析）。

---

## 🗄️ 数据库架构与 Flyway 增量版本

平台基于 PostgreSQL 16+ 设计，采用 Flyway 版本化迁移管理。目前已演进至 **`V20`** 版本，包含 **84 张核心业务表**：

| 迁移版本 | 核心新增数据表与业务能力 | 累计表数 |
|---|---|---|
| `V1`~`V4` | 基础平台租户、广告位、创意、活动、拍卖竞价、网盟核心计划与转化会话表 | 22 张 |
| `V5`~`V9` | 系统安全 RBAC（`sys_user`, `sys_role`, `sys_menu` 等）、系统字典、API 密钥、网盟进阶阶梯佣金与扩展明细表 | 48 张 |
| `V10`~`V13` | 实体结构严格对齐微调、发票实体补齐、宏映射持久化表 (`affiliate_macro_mapping`) | 52 张 |
| `V16`~`V17` | AI 素材自动化 12 张核心表（`creative_material`, `creative_studio_project`, `creative_task_record`, `creative_benchmark_7d` 等） | 64 张 |
| `V18` | 下一代智能化升级表（`affiliate_skan_postback`, `affiliate_bandit_arm`, `creative_agent_action`, `report_hourly_olap_cube` 等） | 79 张 |
| `V19` | 统一 AI 中枢配置与审计表（`ai_model_config`, `ai_prompt_template`, `ai_usage_log`） | 82 张 |
| `V20` | Offer 流量补量规则与推送审计表（`affiliate_offer_supplement_rule`, `affiliate_offer_supplement_log`） | 84 张 |
| `V21` | 生产环境高频查询与外键联合索引加固调优 | 84 张 |
| **`V22`** | **PostgreSQL 范围分区表与定期归档**（`affiliate_click_session_partitioned`, `affiliate_offer_supplement_log_partitioned`, 自动化分区存储过程 `create_monthly_partition`, 定期冷数据归档存储过程 `archive_partitioned_records`, 归档表 `affiliate_click_session_archive`, `affiliate_offer_supplement_log_archive`） | **88 张** |

> 💡 **架构一致性门禁**：工程根目录下提供 `node scripts/audit-entity-schema.mjs`，执行后自动比对 `platform-infrastructure` 中的 88 个 Java Entity 属性与 88 张 Flyway DDL 表结构，**双向保持 100% 字段/类型对齐**。

---

## 🚀 快速开始与本地运行

### 1. 环境准备
- **JDK 21** (推荐 Eclipse Temurin 21 或 OpenJDK 21)
- **Maven 3.9+**
- **Node.js 18.x+ / 20.x+** 与 **pnpm / npm**
- **Docker & Docker Compose** (用于快速拉起 PG、Redis、Kafka)

### 2. 启动依赖基础中间件
在项目根目录下通过 `docker-compose.yml` 快速启动数据库与缓存服务：

```bash
docker-compose up -d postgres redis kafka
```

### 3. 构建与运行全量测试
执行全工程 19 个 Java 模块的构建与自动化测试：

```bash
mvn clean test
```

执行 Entity 与 Flyway Schema 双向对齐校验：

```bash
node scripts/audit-entity-schema.mjs
```

### 4. 启动后端平台服务 (`platform-api`)
```bash
# 本地开发模式启动（自动运行 Flyway V1~V20 增量迁移脚本）
mvn spring-boot:run -pl platform-api
```
后端服务默认监听端口：`http://localhost:8080`，健康检查端点：`http://localhost:8080/actuator/health`。

### 5. 启动现代化前端控制台 (`platform-frontend`)
```bash
cd platform-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```
前端服务默认监听端口：`http://localhost:3000`。
- **默认管理员账号**：`admin`
- **默认密码**：`admin123`

---

## 📡 核心 API 端点速览

```text
# 1. 网盟营销与点击追踪
GET  /affiliate/click                                    # 点击追踪与 12 维 LinUCB 智能分流
GET  /affiliate/postback                                 # S2S 转化回传（Kafka KRaft 批处理/防重）
POST /api/v1/affiliate/offers                            # 创建 Offer 计划
GET  /api/v1/affiliate/offers/supplement/rules           # 查询 Offer 流量补量规则
POST /api/v1/affiliate/offers/supplement/trigger         # 触发 Parquet 补量推送至 AF/Adjust
POST /api/v1/affiliate/offers/supplement/preview         # 预览 S3 存储桶 Parquet 用户画像
POST /api/v1/affiliate/skan/postback                     # Apple SKAN 4.0 隐私归因上报
GET  /api/v1/affiliate/anti-fraud/rules                  # 查询当前 4D 反作弊规则配置
PUT  /api/v1/affiliate/anti-fraud/rules                  # 动态更新反作弊规则（Redis Pub/Sub 广播热生效）
GET  /api/v1/affiliate/anti-fraud/metrics                # 反作弊拦截遥测指标与黑名单规模
POST /api/v1/affiliate/anti-fraud/blacklist/ip           # 动态拉黑作弊 IP
POST /api/v1/affiliate/anti-fraud/blacklist/sub-id       # 动态拉黑作弊 Sub-ID
GET  /api/v1/affiliate/smartlink/ab-report               # 获取 LinUCB 算法 vs 对照组 A/B 测试对比报表

# 2. 程序化交易 (RTB / OpenRTB 2.5)
POST /rtb/openrtb/2.5/bid                                # OpenRTB 2.5 纳秒级竞价入口
POST /rtb/win-notice                                     # 竞价胜出通知与计费确认

# 3. 统一 AI 认知与素材工作台
POST /api/v1/ai/chat                                     # 统一 AI 大模型多租户对话
POST /api/v1/creative/ai/generate-video                  # 异步提交多模型 AI 视频生成
POST /api/v1/creative/studio/projects                    # 创建长视频分镜项目
GET  /api/v1/creative/studio/projects/{id}/export-capcut # 导出剪映/CapCut 官方草稿工程

# 4. 财务与多维分析
GET  /api/v1/billing/entries                             # 查询双式记账分录
POST /api/v1/billing/payouts                             # 生成渠道分成打款批次
GET  /api/v1/reports/olap                                # 查询小时级 OLAP 星型立方体
GET  /api/v1/reports/ai-insights                         # 生成高管 AI 商业洞察
```

---

## 🎯 演进路线图 (Roadmap & Milestones)

平台历时 8 周完成四大里程碑的深度重构与功能落地（全部 100% 验收通过）：

| 周期 (Sprints) | 核心里程碑 | 核心交付物与量化指标 | 状态 |
|---|---|---|---|
| **第 1~2 周 (Milestone 1)** | **P0 稳定性收敛** | 1. 清理全工程 129 处 `catch (Exception ignored)`，规范日志与异常链路；<br>2. 业务核心 Service 移除单机内存回退，生产 100% 依赖 MyBatis-Plus + Redis；<br>3. 实装 Redisson 分布式锁加固 Offer 补量调度、AI 轮询与混剪任务。 | ✅ 100% 落地 |
| **第 3~4 周 (Milestone 2)** | **链路强化与流批落地** | 1. 生产环境正式启用 Kafka KRaft 异步事件消费，内置 Redis 24h 幂等防重；<br>2. 落地 PostgreSQL 2026 按月范围分区表（Flyway V22）与定期自动归档存储过程；<br>3. 前端 Offer 补量抽屉引入 60fps 虚拟滚动容器与响应式参数校验横幅。 | ✅ 100% 落地 |
| **第 5~6 周 (Milestone 3)** | **算法与风控商业化** | 1. 4D 反欺诈风控规则实现前端动态配置与 Redis Pub/Sub 全集群秒级热生效；<br>2. LinUCB Bandit 模型扩充至 12 维特征向量，产出完整 A/B 测试科学对比报表；<br>3. OpenTelemetry 链路追踪全链路接入，产出 APM、风控、补量 3 套 Grafana 看板。 | ✅ 100% 落地 |
| **第 7~8 周 (Milestone 4)** | **服务化与数仓升级** | 1. 引入 ClickHouse 承接实时多维 OLAP 报表，原生 HTTP JSONEachRow 向量化下推；<br>2. K8s 数据面（ZGC 纳秒级）与控制面（G1GC 批处理）物理容器镜像分离部署。 | ✅ 100% 落地 |

详细实施技术分析与验收指标请参阅 **[`docs/EVOLUTION_ROADMAP_MILESTONES_IMPLEMENTATION_REPORT.md`](docs/EVOLUTION_ROADMAP_MILESTONES_IMPLEMENTATION_REPORT.md)**。

---

## 📄 开源与商业协议

本项目归属平台商业授权所有。保留所有权利。未经许可不得反向分发或用于非授权商业运营。
