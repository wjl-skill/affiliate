# Affiliate Platform 文档资产全景导航中枢 (Documentation Knowledge Hub)

> **版本**：v0.2.0-PROD  
> **更新时间**：2026-10-25  
> **适用范围**：工程全体开发、架构、算法、运维与业务运营人员  
> **架构基线**：Java 21 + Spring Boot 3.4.3 (19 个 Maven 模块) + Nuxt 3 (Vue 3/TailwindCSS 23 页面) + PostgreSQL 16 (Flyway V1~V20, 84 张核心表与 84 个 MyBatis-Plus 实体 100% 对齐)

---

## 📚 文档资产总览与分级索引

平台现存 **24 篇权威技术文档与设计规范**，按照架构分层与研发流程划分为六大核心知识域：

```
docs/
├── README.md (本文档 - 全局文档导航与知识中枢)
├── 1. 架构总纲与系统设计 (Architecture & Core Design)
│   ├── ARCHITECTURE.md                                              [系统总体架构与模块Seam设计]
│   ├── CONTEXT.md (根目录)                                          [统一领域词汇与业务概念定义]
│   ├── NEXT_GEN_UPGRADE_AND_IMPLEMENTATION_SPECIFICATION.md         [新一代进阶升级算法与功能实施规范]
│   └── PRODUCTION_MODULE_OPTIMIZATION_AND_HARDENING_DESIGN.md      [全模块深度优化与漏洞修复加固规范]
├── 2. 网盟营销与业务核心 (Affiliate Network & Tracking)
│   ├── AFFILIATE_BUSINESS_REQUIREMENTS.md                           [网盟营销业务需求规格说明书 BRD]
│   ├── AFFILIATE_TECHNICAL_DESIGN.md                                [网盟营销核心技术设计与架构实现 TDD]
│   └── AFFILIATE_API_INTEGRATION_GUIDE.md                           [广告主/渠道客 API 集成对接指南]
├── 3. 算法、归因与安全风控 (Attribution, AI & Anti-Fraud)
│   └── ATTRIBUTION_AND_ANTI_FRAUD_GUIDE.md                          [6大多触点归因算法与商业级反作弊指南]
├── 4. 素材自动化生产体系 (Creative Automation Subsystem)
│   ├── CREATIVE_AUTOMATION_SYSTEM_DESIGN.md                         [素材自动化系统总纲与架构全景]
│   └── creative-automation/
│       ├── README.md                                                [素材自动化专项文档索引]
│       ├── 01-AI_VIDEO_MULTI_MODEL_ENGINE.md                        [多模型 AI 视频生成引擎规范]
│       ├── 02-LONG_VIDEO_STUDIO_PIPELINE.md                         [长视频分镜工作台与 6 管线 Prompt]
│       ├── 03-VIDEO_PROCESSING_AND_AUTO_STITCH.md                   [视频后处理、自适应混剪与画幅裁切]
│       ├── 04-MATERIAL_QUALITY_TAGGING_AND_BENCHMARK.md             [7日大盘基准打标与数仓 ETL 规范]
│       ├── 05-AD_AUTOMATION_AND_LEARNING_PHASE.md                   [跨平台自动建广与广告学习期监控]
│       ├── 06-AI_BILLING_AND_SETTLEMENT.md                          [算力计费两阶段管控与不可变分录]
│       └── CREATIVE_AUTOMATION_BOUNDARY_AND_COMPLETENESS_AUDIT.md   [素材自动化完备性与边界审查报告]
├── 5. 数据基础设施与多模持久化 (Data, Storage & Schema)
│   ├── DATABASE_MIGRATION_GUIDE.md                                  [数据库迁移演进与多级缓存指南]
│   ├── ENTITY_SCHEMA_ALIGNMENT.md                                   [84张表实体结构对齐与审计规约]
│   └── sql/                                                         [模块独立 DDL 脚本库]
└── 6. 运维、前端与性能工程 (Operations, Frontend & Benchmark)
    ├── PRODUCTION_DEPLOYMENT_AND_OPERATIONS_GUIDE.md                [K8s 云原生编排与数据面/控制面运维]
    ├── PERFORMANCE_BENCHMARK_REPORT.md                              [万级 QPS 压测方法论与实测性能基准]
    ├── PRODUCTION_FULL_STACK_OPTIMIZATION_REPORT.md                 [全链路架构升级与优化实施总报告]
    └── FRONTEND_PRODUCTION_GUIDE.md                                 [Nuxt 3 前端生产化指南与开发约定]
```

---

## 🗂️ 核心文档详细导航

### 第一域：架构总纲与系统设计 (Architecture & Core Design)

| 文档名称 | 核心内容与阅读指引 | 核心责任模块 |
|---|---|---|
| **[`docs/ARCHITECTURE.md`](ARCHITECTURE.md)** | **系统总体分层架构**：19 个 Maven 模块的分层拓扑、依赖原则（`api -> 业务 -> common`）、RTB 纳秒级热路径、多租户上下文隔离与持久化模型总括。 | `platform-common`, `platform-api` |
| **[`CONTEXT.md`](../CONTEXT.md)** | **全局领域驱动上下文词汇库**：严格界定 Tenant、Advertiser、Publisher、Affiliate、Campaign、Ad Slot、Click Session、Conversion、MMP、Cap 等术语，消除团队认知偏差。 | 全模块 |
| **[`docs/NEXT_GEN_UPGRADE_AND_IMPLEMENTATION_SPECIFICATION.md`](NEXT_GEN_UPGRADE_AND_IMPLEMENTATION_SPECIFICATION.md)** | **下一代进阶功能实施规范**：LinUCB 上下文多臂老虎机分流算法、Apple SKAdNetwork 4.0 隐私归因解析、营销自治闭环 Agent、分层 OLAP 星型立方体与统一 AI 推理中枢落地规范。 | `platform-affiliate`, `platform-ai`, `platform-creative`, `platform-reporting` |
| **[`docs/PRODUCTION_MODULE_OPTIMIZATION_AND_HARDENING_DESIGN.md`](PRODUCTION_MODULE_OPTIMIZATION_AND_HARDENING_DESIGN.md)** | **全系统优化加固规范**：10 大核心逻辑漏洞修复、资金防超扣、RTB 阻尼出价、双模持久化一致性保障与生产容灾预案。 | `platform-budget`, `platform-billing`, `platform-dsp`, `platform-adx` |

---

### 第二域：网盟营销与业务核心 (Affiliate Network & Tracking)

| 文档名称 | 核心内容与阅读指引 | 核心责任模块 |
|---|---|---|
| **[`docs/AFFILIATE_BUSINESS_REQUIREMENTS.md`](AFFILIATE_BUSINESS_REQUIREMENTS.md)** | **网盟商业化需求规范 (BRD)**：涵盖渠道分级管理、Offer 投放与 Cap 配额、智能分流（SmartLink/TDS）、S2S 转化回传、Sub-ID 多维报表、流量补量与周期结算完整商业逻辑。 | `platform-affiliate` |
| **[`docs/AFFILIATE_TECHNICAL_DESIGN.md`](AFFILIATE_TECHNICAL_DESIGN.md)** | **网盟核心技术实现方案 (TDD)**：点击追踪热路径优化（P95<50ms）、Postback 幂等去重、Redis 预占原子锁、Parquet 用户画像解析、AF/Adjust MMP 推送与 Net-7/15/30 账期结算设计。 | `platform-affiliate`, `platform-infrastructure` |
| **[`docs/AFFILIATE_API_INTEGRATION_GUIDE.md`](AFFILIATE_API_INTEGRATION_GUIDE.md)** | **外部合作伙伴对接手册**：面向广告主与渠道客的 HTTP 接口规范，包含点击跳转、S2S Postback 接收、Offer 查询、转化状态回调与多语言（Python/Go/Java/Node）集成示例。 | `platform-affiliate`, `platform-api` |

---

### 第三域：算法、归因与安全风控 (Attribution, AI & Anti-Fraud)

| 文档名称 | 核心内容与阅读指引 | 核心责任模块 |
|---|---|---|
| **[`docs/ATTRIBUTION_AND_ANTI_FRAUD_GUIDE.md`](ATTRIBUTION_AND_ANTI_FRAUD_GUIDE.md)** | **归因算法与商业级反欺诈**：<br>1. 六大归因模型详解（First/Last/Linear/Time-Decay/U-Shaped/Data-Driven MTA）与精确到分平账机制；<br>2. 4D 反欺诈风控引擎（CTIT 漏斗异常、超音速跨国漂移、设备/系统突变、数据中心爬虫 IP、分钟级转化泛滥拦截）。 | `platform-affiliate`, `platform-cdp`, `platform-ai` |

---

### 第四域：素材自动化生产体系 (Creative Automation Subsystem)

| 文档名称 | 核心内容与阅读指引 | 核心责任模块 |
|---|---|---|
| **[`docs/CREATIVE_AUTOMATION_SYSTEM_DESIGN.md`](CREATIVE_AUTOMATION_SYSTEM_DESIGN.md)** | **素材自动化总体架构总纲**：多模型 AI 视频生成、7 阶段长视频流水线、数字人驱动、CapCut 剪映工程草稿微秒级导出、7 日大盘基准打标与算力分账闭环。 | `platform-creative` |
| **[`docs/creative-automation/README.md`](creative-automation/README.md)** | **素材自动化分册导航索引**：专题深入设计文档目录与架构拓扑。 | `platform-creative` |
| **[`docs/creative-automation/01-AI_VIDEO_MULTI_MODEL_ENGINE.md`](creative-automation/01-AI_VIDEO_MULTI_MODEL_ENGINE.md)** | **多模型 AI 视频生成引擎**：统一异步任务转译、供应商限流熔断、Java 21 虚拟线程长轮询编排。 | `platform-creative` |
| **[`docs/creative-automation/02-LONG_VIDEO_STUDIO_PIPELINE.md`](creative-automation/02-LONG_VIDEO_STUDIO_PIPELINE.md)** | **长视频 Studio 工作台**：分镜剧本拆解、6 管线 Prompt 优化、首尾关键帧过渡、CapCut 标准草稿导出。 | `platform-creative` |
| **[`docs/creative-automation/03-VIDEO_PROCESSING_AND_AUTO_STITCH.md`](creative-automation/03-VIDEO_PROCESSING_AND_AUTO_STITCH.md)** | **视频后处理与混剪**：MediaProbe 媒体嗅探、画幅智能裁切、字幕压制与自适应拼接调度。 | `platform-creative` |
| **[`docs/creative-automation/04-MATERIAL_QUALITY_TAGGING_AND_BENCHMARK.md`](creative-automation/04-MATERIAL_QUALITY_TAGGING_AND_BENCHMARK.md)** | **质量打标与数仓 ETL**：7 日大盘动态基准线打标（`creative_benchmark_7d`）、高潜素材自动分流。 | `platform-creative` |
| **[`docs/creative-automation/05-AD_AUTOMATION_AND_LEARNING_PHASE.md`](creative-automation/05-AD_AUTOMATION_AND_LEARNING_PHASE.md)** | **自动化投放与学习期监控**：主流媒体 API 批量建广、冷启动学习期智能监控与自动止损。 | `platform-creative` |
| **[`docs/creative-automation/06-AI_BILLING_AND_SETTLEMENT.md`](creative-automation/06-AI_BILLING_AND_SETTLEMENT.md)** | **AI 算力计费与分账**：预估冻结、实际扣减、不可变双式记账分录与 AI ROI 商业度量。 | `platform-creative`, `platform-billing` |
| **[`docs/creative-automation/CREATIVE_AUTOMATION_BOUNDARY_AND_COMPLETENESS_AUDIT.md`](creative-automation/CREATIVE_AUTOMATION_BOUNDARY_AND_COMPLETENESS_AUDIT.md)** | **完备性与边界审查报告**：12 项关键边界问题修复复盘、虚拟线程调度安全与全链路测试验收。 | `platform-creative` |

---

### 第五域：数据基础设施与多模持久化 (Data, Storage & Schema)

| 文档名称 | 核心内容与阅读指引 | 核心责任模块 |
|---|---|---|
| **[`docs/DATABASE_MIGRATION_GUIDE.md`](DATABASE_MIGRATION_GUIDE.md)** | **数据库接入与多级缓存实施指南**：三级缓存架构（Caffeine + Redis + PostgreSQL）、数据流向、双模回退机制与生产演进规范。 | `platform-infrastructure` |
| **[`docs/ENTITY_SCHEMA_ALIGNMENT.md`](ENTITY_SCHEMA_ALIGNMENT.md)** | **实体与数据库结构对齐规约**：84 张表与 84 个 MyBatis-Plus 实体 100% 双向匹配规范与 `audit-entity-schema.mjs` 门禁说明。 | `platform-infrastructure` |
| **`platform-infrastructure/src/main/resources/db/migration/`** | **Flyway 增量迁移脚本集 (V1~V20)**：涵盖基础租户/广告交易、网盟全链路、系统权限配置、素材自动化 12 表、进阶 SKAN/Agent 表、AI 中枢表与 V20 流量补量表。 | `platform-infrastructure` |

---

### 第六域：运维、前端与性能工程 (Operations, Frontend & Benchmark)

| 文档名称 | 核心内容与阅读指引 | 核心责任模块 |
|---|---|---|
| **[`docs/PRODUCTION_DEPLOYMENT_AND_OPERATIONS_GUIDE.md`](PRODUCTION_DEPLOYMENT_AND_OPERATIONS_GUIDE.md)** | **生产部署与运维操作指南**：数据面（Dataplane，极致低延迟）与控制面（Controlplane，重业务处理）物理隔离部署、Docker Compose、K8s/GitOps 编排、Prometheus 监控探针。 | 全模块, `k8s/` |
| **[`docs/PERFORMANCE_BENCHMARK_REPORT.md`](PERFORMANCE_BENCHMARK_REPORT.md)** | **端到端高并发压测报告**：k6 万级阶梯压测脚本、实测 152k QPS / P99 2.67ms 延时分布与性能调优要素。 | `perf-tests/` |
| **[`docs/PRODUCTION_FULL_STACK_OPTIMIZATION_REPORT.md`](PRODUCTION_FULL_STACK_OPTIMIZATION_REPORT.md)** | **生产级架构升级实施总报告**：全模块构建验收、测试覆盖率矩阵与生产演进基准记录。 | 全模块 |
| **[`docs/FRONTEND_PRODUCTION_GUIDE.md`](FRONTEND_PRODUCTION_GUIDE.md)** | **Vue/Nuxt 3 控制台生产规范**：23 个页面路由契约、多租户 Header 注入、状态管理、生产构建检查清单。 | `platform-frontend` |

---

## 🧭 研发人员快速检索路线

```mermaid
flowchart TD
    Start([开始使用/开发]) --> Role{你的角色/任务}
    
    Role -->|新员工入职 / 全局理解| Path1[1. CONTEXT.md 熟悉词汇<br>2. ARCHITECTURE.md 了解分层<br>3. 根目录 README.md 启动工程]
    Role -->|网盟业务 / 追踪开发| Path2[1. AFFILIATE_BUSINESS_REQUIREMENTS.md 业务逻辑<br>2. AFFILIATE_TECHNICAL_DESIGN.md 架构设计<br>3. AFFILIATE_API_INTEGRATION_GUIDE.md 接口定义]
    Role -->|素材自动化 / AI Studio| Path3[1. CREATIVE_AUTOMATION_SYSTEM_DESIGN.md 总体设计<br>2. docs/creative-automation/01~06 专题方案]
    Role -->|前端开发 (Nuxt 3)| Path4[1. FRONTEND_PRODUCTION_GUIDE.md 约定<br>2. platform-frontend/pages/ 业务页面]
    Role -->|数据库修改 / 新增实体| Path5[1. V20 增量 Flyway SQL<br>2. platform-infrastructure 增加 Entity/Mapper<br>3. 运行 node scripts/audit-entity-schema.mjs 校验]
    Role -->|生产发布 / 运维调优| Path6[1. PRODUCTION_DEPLOYMENT_AND_OPERATIONS_GUIDE.md<br>2. PERFORMANCE_BENCHMARK_REPORT.md]
```
