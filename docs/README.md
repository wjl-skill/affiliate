# Affiliate Platform 文档资产全景导航中枢 (Documentation Knowledge Hub)

> **版本**：v3.0.0 Commercial Release  
> **更新时间**：2026-10-27  
> **适用范围**：工程全体开发、架构、算法、运维与业务运营人员  
> **架构基线**：Java 21 + Spring Boot 3.4.3 (21 个 Maven 模块) + Nuxt 3 (Vue 3/TailwindCSS 23 页面) + PostgreSQL 16 (Flyway V1~V22, 88 张核心表与 88 个 MyBatis-Plus 实体 100% 对齐) + ClickHouse OLAP + Kafka KRaft + Redisson 分布式锁

---

## 📚 文档资产总览与核心知识域

平台经过深度重构与业务文档整合，梳理归集为 **6 大核心权威技术文档与数据脚本库**，按照架构分层与业务闭环严格组织：

```
docs/
├── README.md                                          [本文档 - 全局文档导航与知识中枢]
├── 1. 系统架构与总纲 (Architecture & Core Seams)
│   ├── ARCHITECTURE.md                                [系统总体分层架构、21个模块拓扑与依赖Seam设计]
│   └── CONTEXT.md (工程根目录)                         [全局领域驱动上下文词汇库与统一业务术语]
├── 2. 网盟营销核心业务与系统设计 (Affiliate Business & System Manual)
│   ├── AFFILIATE_BUSINESS_AND_SYSTEM_MANUAL.md        [网盟核心业务规范、技术设计、MTA多触点归因与4D反作弊风控手册]
│   └── AFFILIATE_API_INTEGRATION_GUIDE.md             [外部合作伙伴对接手册: 点击跳转、S2S Postback与多广告/媒体平台连接器 API]
├── 3. AI 创意自动化生产体系 (Creative Automation Subsystem)
│   └── CREATIVE_AUTOMATION_SYSTEM_GUIDE.md            [AI 视频多模型引擎、长视频工作台、画幅对齐混剪、大盘打标与算力分账完整指南]
├── 4. 数据基础设施与多模持久化 (Data, Storage & Schema)
│   ├── DATABASE_AND_MIGRATION_GUIDE.md                [数据库接入、多级缓存架构、88张表实体对齐审计与分区归档实施指南]
│   └── sql/                                           [模块独立 DDL 脚本库 (01~17 独立建表语句)]
└── 5. 运维部署与生产规范 (Operations, Deployment & Frontend)
    └── PRODUCTION_OPERATIONS_AND_DEPLOYMENT_GUIDE.md  [K8s云原生编排、数据面/控制面物理分离、Prometheus可观测性与Nuxt 3前端控制台规范]
```

---

## 🗂️ 核心文档详细导航与阅读指引

### 第一域：架构总纲与系统设计 (Architecture & Core Seams)

| 文档名称 | 核心内容与阅读指引 | 核心责任模块 |
|---|---|---|
| **[`docs/ARCHITECTURE.md`](ARCHITECTURE.md)** | **系统总体分层架构**：21 个 Maven 模块的分层拓扑、依赖原则（`api -> 业务 -> common`）、RTB 纳秒级热路径、多租户上下文隔离与双平面（Data-Plane / Control-Plane）解耦总括。 | `platform-common`, `platform-api` |
| **[`CONTEXT.md`](../CONTEXT.md)** | **全局领域驱动上下文词汇库**：严格界定 Tenant、Advertiser、Publisher、Affiliate、Campaign、Ad Slot、Click Session、Conversion、MMP、Cap 等术语，消除团队认知偏差。 | 全模块 |

---

### 第二域：网盟营销核心业务与系统设计 (Affiliate Business & System Manual)

| 文档名称 | 核心内容与阅读指引 | 核心责任模块 |
|---|---|---|
| **[`docs/AFFILIATE_BUSINESS_AND_SYSTEM_MANUAL.md`](AFFILIATE_BUSINESS_AND_SYSTEM_MANUAL.md)** | **网盟核心业务与系统权威手册**：<br>1. **业务画像与规则**：渠道客生命周期、阶梯分润模型（VIP 佣金上浮）、Offer 定向与容量上限熔断；<br>2. **链路架构**：纳秒级点击跳转、302 重定向、宏替换与 Sub-ID 5 维下钻穿透；<br>3. **转化与归因**：S2S 幂等回传、6 大多触点归因模型（含 Data-Driven MTA 算法公式）与分币平账；<br>4. **4D 动态反作弊**：CTIT 漏斗异常、超音速跨国漂移、设备/系统突变、数据中心机房 IP、分钟级转化泛滥拦截；<br>5. **财务对账**：Net-7/15/30 账期自动化出账、不可变复式记账流水与批量打款。 | `platform-affiliate`, `platform-billing`, `platform-reporting` |
| **[`docs/AFFILIATE_API_INTEGRATION_GUIDE.md`](AFFILIATE_API_INTEGRATION_GUIDE.md)** | **外部合作伙伴集成与 API 手册**：<br>1. 面向广告主与渠道客的公网端点（点击跳转、S2S Postback 接收、Offer 查询与发票状态）；<br>2. 统一多广告平台连接器（Google Ads、TikTok Ads、Facebook Ads 报表与 CAPI 转化上报）；<br>3. 统一媒体侧变现网络（Google GAM/AdMob、TikTok Pangle、Meta Audience Network 变现收益与库存同步）；<br>4. 多语言（Python/Go/Java/Node/Ruby/PHP）实战调用示例。 | `platform-affiliate`, `platform-ad-connectors`, `platform-media-connectors` |

---

### 第三域：AI 创意自动化生产体系 (Creative Automation Subsystem)

| 文档名称 | 核心内容与阅读指引 | 核心责任模块 |
|---|---|---|
| **[`docs/CREATIVE_AUTOMATION_SYSTEM_GUIDE.md`](CREATIVE_AUTOMATION_SYSTEM_GUIDE.md)** | **AI 素材自动化闭环总纲**：<br>1. **多模型引擎**：`ModelMeta + Generator + Provider` 三层解耦、火山引擎 Seedance 2.0 与阿里百炼 DashScope 统一转译；<br>2. **长视频工作台**：7 阶段工程流水线、6 大行业 Prompt 管线、边界关键帧法 ($K_0 \sim K_n$) 与剪映草稿协议导出；<br>3. **智能混剪**：MediaProbe 媒体探针、画幅自适应硬拼接 (Auto-Stitch) 与 5 大原子算子；<br>4. **大盘打标**：7 天滑动窗口 Benchmark 计算 SQL、三重样本门槛与 6 级质量状态机；<br>5. **建广与计费**：Meta/TikTok 批量建广、学习期熔断、算力计费与虚拟钱包预占实扣。 | `platform-creative`, `platform-billing` |

---

### 第四域：数据基础设施与多模持久化 (Data, Storage & Schema)

| 文档名称 | 核心内容与阅读指引 | 核心责任模块 |
|---|---|---|
| **[`docs/DATABASE_AND_MIGRATION_GUIDE.md`](DATABASE_AND_MIGRATION_GUIDE.md)** | **数据持久化与数据库治理指南**：<br>1. **三级缓存架构**：L1 Caffeine (<1ms) $\rightarrow$ L2 Redis (1~10ms) $\rightarrow$ L3 PostgreSQL，缓存穿透防护与键生成规约；<br>2. **Flyway 演进**：V1~V22 增量迁移脚本与兼容性补列；<br>3. **实体对齐与审计**：88 张核心表与 88 个 MyBatis-Plus 实体 100% 对齐规约，配有 `scripts/audit-entity-schema.mjs` 自动化审计工具；<br>4. **范围分区分表**：PostgreSQL 按月分区表与冷数据定期归档策略。 | `platform-infrastructure`, `platform-common` |
| **[`docs/sql/`](sql/)** | **模块独立 DDL 脚本库**：包含 `01_platform_tenant.sql` 至 `17_next_gen_olap_and_skan.sql` 等 17 个子模块独立建表与索引脚本，以及本地快速初始化的 `all_modules_schema.sql`。 | `platform-infrastructure` |

---

### 第五域：运维部署与生产规范 (Operations, Deployment & Frontend)

| 文档名称 | 核心内容与阅读指引 | 核心责任模块 |
|---|---|---|
| **[`docs/PRODUCTION_OPERATIONS_AND_DEPLOYMENT_GUIDE.md`](PRODUCTION_OPERATIONS_AND_DEPLOYMENT_GUIDE.md)** | **生产云原生部署与运维实操手册**：<br>1. **双平面物理拓扑**：数据面 (Dataplane, 万级 QPS 热路径) 与控制面 (Controlplane, 批处理管理) 物理分离部署；<br>2. **云原生编排**：Docker 多阶段构建、Kubernetes Ingress 路由、HPA 自动弹性伸缩、PDB 容灾保障；<br>3. **可观测性**：Prometheus 抓取配置、Actuator 三级探针、分布式链路追踪 `X-Trace-Id` 与日志联动；<br>4. **前端运维约定**：Nuxt 3 控制台生产环境变量、API 上下文拦截器、各管理页面写操作约定与发布前检查命令。 | 全系统, `platform-frontend`, `platform-api` |

---

## 🎯 快速上手阅读路径

- **新开发者入职**：阅读 [`CONTEXT.md`](../CONTEXT.md) $\rightarrow$ [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) $\rightarrow$ [`docs/AFFILIATE_BUSINESS_AND_SYSTEM_MANUAL.md`](AFFILIATE_BUSINESS_AND_SYSTEM_MANUAL.md)；
- **第三方客户/推手对接**：阅读 [`docs/AFFILIATE_API_INTEGRATION_GUIDE.md`](AFFILIATE_API_INTEGRATION_GUIDE.md)；
- **AI 创意算法与多媒体开发**：阅读 [`docs/CREATIVE_AUTOMATION_SYSTEM_GUIDE.md`](CREATIVE_AUTOMATION_SYSTEM_GUIDE.md)；
- **DBA 与数仓建模人员**：阅读 [`docs/DATABASE_AND_MIGRATION_GUIDE.md`](DATABASE_AND_MIGRATION_GUIDE.md) 及 [`docs/sql/`](sql/)；
- **SRE 与 DevOps 运维人员**：阅读 [`docs/PRODUCTION_OPERATIONS_AND_DEPLOYMENT_GUIDE.md`](PRODUCTION_OPERATIONS_AND_DEPLOYMENT_GUIDE.md)。
