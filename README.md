# Affiliate Platform

面向程序化广告和网盟业务的 Java 21 / Spring Boot 3.4 多模块平台，覆盖 DSP、SSP、ADX、广告素材、DMP、CDP、预算、事件、Google 连接器、计费和报表等边界。

当前交付形态是**模块化单体**：`platform-api` 是可直接部署的 Spring Boot 应用，其余模块以独立 jar 参与构建。模块之间保持明确依赖方向，后续可以按 RTB、事件或报表边界拆分为微服务。

> 当前代码提供可运行的内存适配器和真实的 OpenRTB/OAuth 基础流程，适合功能联调和架构验证。生产环境必须启用 PostgreSQL、Redis、Kafka、JWT 和密钥管理，并补齐供应商 SDK、数据合规和压测验收。

## 文档入口

### 核心文档
- [领域词汇 CONTEXT.md](CONTEXT.md)：统一 DMP、CDP、DSP、SSP、ADX 等业务语言。
- [详细架构设计 ARCHITECTURE.md](docs/ARCHITECTURE.md)：模块边界、依赖方向、RTB 热路径、租户隔离、数据模型、事件和生产部署约束。
- [系统设计优化 SYSTEM_DESIGN_OPTIMIZATION.md](docs/SYSTEM_DESIGN_OPTIMIZATION.md)：性能优化与扩展策略。

### 网盟营销 (Affiliate Network) 专项文档
- **[商业需求文档 AFFILIATE_BUSINESS_REQUIREMENTS.md](docs/AFFILIATE_BUSINESS_REQUIREMENTS.md)**
  完整的业务功能规格说明，涵盖渠道管理、Offer 配置、点击追踪、转化归因、SmartLink/TDS 智能分流、反欺诈、Sub-ID 多维分析、账期结算和支付处理等核心业务流程。对标 CJ Affiliate、ShareASale、Rakuten Advertising、Impact、Awin 等商业级网盟平台。

- **[技术设计文档 AFFILIATE_TECHNICAL_DESIGN.md](docs/AFFILIATE_TECHNICAL_DESIGN.md)**
  详细的技术架构与实现方案，包括高并发点击追踪（P95<50ms）、S2S 转化回传、Redis 预算 Cap 原子控制、Kafka 异步事件流、商业级反欺诈引擎（CTIT、IP/UA 特征、黑名单）、SmartLink EPC 优化路由、Publisher Postback 分发、分布式账期结算作业等核心技术实现。

- **[API 集成指南 AFFILIATE_API_INTEGRATION_GUIDE.md](docs/AFFILIATE_API_INTEGRATION_GUIDE.md)**
  面向广告主与渠道客的完整 API 对接手册，包含点击追踪、S2S Postback 转化上报、Offer 管理、渠道管理、多维报表查询、Webhook/Postback 配置等接口规范，以及 PHP、Python、Node.js、Ruby 等主流语言的代码示例。

### 数据库设计
- [数据库迁移](platform-infrastructure/src/main/resources/db/migration/V1__platform_schema.sql)：PostgreSQL/Flyway 初始表结构。
- [网盟营销 SQL 脚本](docs/sql/13_platform_affiliate.sql)：完整的网盟营销业务表结构，包含渠道客、Offer、阶梯出价、SmartLink、点击会话、转化流水、结算发票和 Sub-ID 统计表。

## 架构总览

```text
                         ┌──────────────────────┐
                         │     platform-api     │
                         │ Spring Boot / REST  │
                         └──────────┬───────────┘
                                    │
      ┌──────────────┬──────────────┼──────────────┬──────────────┐
      │              │              │              │              │
   Identity       Supply         Demand          Auction        Data
 auth/tenant   creative/ssp    dsp/budget        adx/RTB      dmp/cdp
      │              │              │              │              │
      └──────────────┴──────────────┴──────┬───────┴──────────────┘
                                           │
                         event / billing / reporting
                                           │
                         infrastructure adapters
                     PostgreSQL · Redis · Kafka · Flyway
```

依赖原则：`platform-api -> 业务模块 -> platform-common`；基础设施只实现端口，不反向依赖 API。`platform-common` 仅保存稳定领域模型、仓储端口和通用异常。

## 模块与功能

| 模块 | 当前职责和已实现能力 |
|---|---|
| `platform-common` | 共享领域记录：Creative、AdSlot、Auction、PartnerConnection、枚举、内存仓储和通用异常。 |
| `platform-auth` | Spring Security、JWT Resource Server 条件配置、RBAC 基础、`TenantContext` 和请求租户过滤器。 |
| `platform-tenant` | 租户生命周期；DSP/SSP/ADX 合作方连接配置、状态管理和同步入口。 |
| `platform-creative` | 图片/视频/原生/HTML5 素材创建、查询、激活和领域事件。 |
| `platform-ssp` | 媒体广告位、尺寸、底价、安全属性和激活状态。 |
| `platform-dsp` | Campaign 创建、激活/暂停、日期/域名/设备定向和 Campaign 匹配。 |
| `platform-dmp` | 匿名 Cookie/设备 ID 受众分群、成员导入、TTL 过期判断和广告激活查询。 |
| `platform-cdp` | 第一方手机号/OpenID/会员号身份映射、客户画像合并、解析、opt-out 和擦除。 |
| `platform-adx` | OpenRTB 2.5 请求解析、素材筛选、底价判断、规则出价和 Auction 记录。 |
| `platform-budget` | `reserve/confirm/release` 预算端口；本地 CAS 和 Redis 计数器适配器。 |
| `platform-event` | DomainEvent、内存 Outbox、JDBC Outbox 和 Kafka 发布器。 |
| `platform-google-ads` | Google OAuth authorization-code 流程，服务端交换 code，回调不返回 token 原文。 |
| `platform-google-gam` | `AdPlatformConnector` 抽象和 GAM bearer-token 同步占位实现。 |
| `platform-billing` | BigDecimal 计费分录、租户查询、虚拟钱包余额预占扣款与媒体分成结算。 |
| `platform-reporting` | 租户维度的日聚合记录、CTR/CVR/eCPM/eCPC/CPA/ROI 衍生效果指标分析引擎。 |
| `platform-affiliate` | 商业级网盟营销核心：Offer、SmartLink/TDS 智能分流、Click 宏追踪、S2S Postback 归因、CTIT 反作弊、渠道回传分发、Net-7/15/30 账期结算和 Sub-ID 报表。 |
| `platform-infrastructure` | JDBC/Flyway/PostgreSQL、Redis、Kafka 依赖、二级缓存与 Flyway 数据库迁移脚本。 |
| `platform-api` | 应用启动、模块组合、仓储 Bean 装配、统一异常响应和打包入口。 |

## 核心业务链路

### 1. RTB 程序化竞价交易链路

1. SSP/ADX 调用 `POST /rtb/openrtb/2.5/bid`。
2. ADX 校验 Bid Request，并根据 `imp.id` 查找已激活广告位与动态底价（Dynamic Floor Price）。
3. DSP 策略按国家、设备、分时排期（Dayparting 7x24）与人群包筛选素材，采用 oCPM 公式智能出价，第一价环境执行 Bid Shading 调优。
4. ADX 执行第一价或第二价 Vickrey 次高价清算（支持 PMP Deal ID 优先清算），动态展开 OpenRTB 宏代码（`${AUCTION_PRICE}` 等）。
5. 成交后记录 Auction；Win、Impression、Click、Conversion 通过领域事件异步处理，当前 RTB 撮合 P99 延迟稳定在 0.101 ms。

### 2. 效果营销网盟链路 (Affiliate Network Pipeline)

1. **点击追踪与 TDS 智能分流**：
   - 渠道客推广专属跟踪链接：`GET /affiliate/click?offer_id=101&aff_id=888&sub1=fb&sub2=ad01` 或 SmartLink 统一分流链接。
   - 系统防欺诈质检（单 IP 泛洪拦截）$\rightarrow$ TDS 路由引擎（按国家、设备与历史最高 EPC 智能优选）$\rightarrow$ 检查单日转化 Cap（超限自动跳转 Fallback Offer）。
   - 生成全局唯一加密 `click_id`，落盘 30 天点击会话存根，动态替换落地页宏参数，返回 HTTP 302 重定向至广告主落地页。
2. **广告主 S2S 服务端转化回传与归因**：
   - 广告主完成转化后调用：`GET/POST /affiliate/postback?click_id={click_id}&txid={order_id}&sale_amount=100.00`。
   - 匹配原始点击会话，对 `txid` 严格执行幂等查重，计算 **CTIT 转化耗时差**（$< 3\text{s}$ 自动触发点击注入/脚本作弊预警）。
   - 依据基准或 VIP 专属阶梯加价核算佣金，扣减日 Cap。
   - 异步对下游渠道预留的 Postback URL 执行宏代码替换并异步分发通知。
3. **财务审核与周期结算出账**：
   - 转化处于缓冲期（`PENDING` $\rightarrow$ `APPROVED` / `REJECTED`）。
   - 依据渠道约定的 Net-7 / Net-15 / Net-30 账期，在佣金达到起提门槛（如 \$100）时自动生成正式结算发票。
4. **Sub-ID 多维流式分析**：
   - 实时聚合渠道及 `sub1`~`sub5` 维度的点击量、转化量、EPC、CR%、RPC 与利润率。

### 3. DMP 与 CDP 数据边界

- DMP 只处理匿名、短生命周期标识，服务程序化广告定向、规则圈选和 Jaccard 相似度 Lookalike 扩量。
- CDP 只处理租户合法收集的第一方实名标识，服务置信度图谱（Identity Graph）、360度客户行为事件时间流和 RFM 客户价值分层模型。
- 两个上下文默认禁止反向身份关联；跨上下文操作必须经过租户授权和同意校验。

## 数据库架构与 SQL 目录索引

平台全面支持 PostgreSQL 14+，提供完备的 DDL、强类型字段、中文注释、索引与外键约束：

- **Flyway 生产自动迁移脚本**（`platform-infrastructure/src/main/resources/db/migration/`）：
  - `V1__platform_schema.sql`：基础平台骨架表（租户、物料、广告位、竞价、事件、日志等）
  - `V2__ad_entities.sql`：广告实体扩展表
  - `V3__auction_and_connections.sql`：拍卖历史与三方生态连接表
  - `V4__affiliate_network_schema.sql`：网盟营销体系全套表（Offer、渠道客、SmartLink、点击存根、转化流水、发票账单、Sub-ID 报表）
- **独立模块 SQL 脚本规范**（`docs/sql/` 与 `doc/sql/`）：
  - `01_platform_tenant.sql`：多租户与配额 SLA
  - `02_platform_creative.sql`：素材生命周期与合规审核
  - `03_platform_ssp.sql`：媒体广告位与动态底价
  - `04_platform_dsp.sql`：广告活动、AdGroup 定向与 oCPM 策略
  - `05_platform_adx.sql`：实时竞价拍卖清算与成交审计
  - `06_platform_budget.sql`：预算配额、平滑 Pacing 与阶梯阈值告警
  - `07_platform_event.sql`：可靠事件 Outbox 与事件追踪
  - `08_platform_dmp.sql`：受众群体、行为特征与 Lookalike
  - `09_platform_cdp.sql`：一方客户画像、置信度图谱与 RFM 模型
  - `10_platform_billing.sql`：双式记账分录、账户钱包与分成结算
  - `11_platform_reporting.sql`：多维报表增量聚合与衍生效益指标
  - `12_platform_connectors.sql`：Google Ads / GAM 三方连接凭据
  - `13_platform_affiliate.sql`：网盟 Offer、TDS 智能分流、S2S 转化与账期出账
  - `all_modules_schema.sql`：全系统 13 大子域一键初始化完整整合 SQL

## REST API

### 管理接口

```text
POST/GET /api/v1/tenants
POST/GET /api/v1/partners
POST/GET /api/v1/creatives
POST/GET /api/v1/ad-slots
POST/GET /api/v1/campaigns
GET      /api/v1/campaigns/match?domain=example.com&deviceType=1
POST/GET /api/v1/dmp/segments
POST     /api/v1/dmp/segments/{id}/members
GET      /api/v1/dmp/segments/{id}/contains?anonymousId=...
POST/GET /api/v1/cdp/profiles
GET      /api/v1/cdp/profiles/resolve?identifier=...
POST     /api/v1/cdp/profiles/{primaryId}/merge
POST     /api/v1/cdp/profiles/{primaryId}/opt-out
DELETE   /api/v1/cdp/profiles/{primaryId}
POST/GET /api/v1/billing/entries
GET      /api/v1/reports/daily?from=YYYY-MM-DD&to=YYYY-MM-DD
GET      /api/v1/google/oauth/authorize
GET      /api/v1/google/oauth/callback
```

### 交易接口

```text
POST /rtb/openrtb/2.5/bid
```

默认开发模式使用 `X-Tenant-ID` 建立租户上下文；生产模式使用 JWT `tenant_id`/`tenant` claim，客户端 header 不应覆盖可信 claim。

## 本地运行

环境要求：Java 21、Maven 3.9+。

```bash
mvn clean -pl platform-api -am test
mvn -pl platform-api -am spring-boot:run
```

默认监听 `http://localhost:8080`，健康检查和指标：

```text
GET /actuator/health
GET /actuator/metrics
```

默认 profile 关闭外部数据库、Redis、Kafka，使用并发内存实现，便于本地联调。

## 生产 profile

```bash
mvn -pl platform-api -am spring-boot:run -Dspring-boot.run.profiles=prod
```

至少配置以下环境变量，并通过 Secret Manager/Vault 注入敏感值：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_URL
KAFKA_BOOTSTRAP_SERVERS
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
GOOGLE_REDIRECT_URI
```

Docker 镜像使用 [Dockerfile](F:/Affiliate/Dockerfile)，构建前先生成 `platform-api/target/platform-api-0.2.0.jar`。

## 一致性与高并发约束

- RTB 热路径不得同步调用 Google 或其他慢供应商，不得使用无界线程池。
- 预算和频控使用 Redis Lua/条件更新实现原子扣减，故障时默认 fail-closed。
- 业务写入和 Outbox 事件应在同一事务提交，Kafka 消费者按 event id 幂等。
- Billing Entry 只追加不更新，`idempotencyKey` 唯一。
- 所有业务资源、缓存 key、事件 key 和分录必须包含 `tenant_id`。
- 关键监控：RTB 延迟、timeout、no-bid、win rate、预算拒绝、连接器延迟、Outbox lag、Kafka lag 和对账差异。

## 当前实现状态

### 已可运行

- 17 个 Maven 模块聚合构建。
- OpenRTB 2.5 基础竞价链路。
- 素材、广告位、租户、合作方、Campaign、DMP、CDP、计费、报表 REST 管理接口。
- JWT/RBAC 条件化配置和租户上下文。
- Kafka/Outbox、Redis、PostgreSQL/Flyway 的适配边界。
- Google OAuth state 一次性消费和五分钟过期校验。

### 生产化待办

- 将内存仓储替换为 tenant-aware PostgreSQL Repository，并补齐 DMP/CDP 表、索引和分区策略。
- Redis Lua 预算预占、频控、幂等键和故障恢复校准。
- Kafka Outbox Relay、事件事实表和迟到事件重算。
- Google Ads/GAM 官方 SDK、配额管理、重试、凭据加密和 Vault/KMS。
- 完整 RBAC 资源授权、审计日志、用户同意、删除请求和数据保留策略。
- 计费结算、合作方对账、发票和财务报表。
- OpenTelemetry、限流、熔断、压测、SLO 告警和多副本部署。

## 验证

当前主验证命令：

```bash
mvn clean -pl platform-api -am test
```

该命令会构建所有依赖模块并执行模块测试，包括 DSP Campaign、DMP 受众和 CDP 身份映射测试。
#   a f f i l i a t e 
 
 
