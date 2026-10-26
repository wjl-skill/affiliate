# 实施任务分解与执行落地状态跟踪

> 所属: 商业级演化详细设计 (阶段 0–2)。总览见 [README](README.md)。

| # | 任务 | 阶段 | 估时 | 核心落地模块与类 | 状态 | 验收达成 |
|---|---|---|---|---|---|---|
| T1 | 密钥轮换 + ExternalSecret + 历史清理 + gitleaks | 0 | 3d | `k8s/externalsecret.yaml`, `k8s/secret.example.yaml`, `.github/workflows/ci-cd.yml` | ✅ 已完成 | 仓库无明文密钥，CI/CD 注入 Gitleaks 静态安全审计 |
| T2 | `ProdConfigGuard` + Kafka trusted packages | 0 | 2d | `ProdConfigGuard.java`, `application-prod.yml` | ✅ 已完成 | 生产 Profile 下严格断言 JWT 熵值、S2S 开关、PostgreSQL/Redis 凭据，缺配置即阻断启动 |
| T3 | 权限码体系 + `@PreAuthorize` 全覆盖 + `@PublicEndpoint` | 0 | 8d | `RbacService.java`, `PermissionService.java`, `@PublicEndpoint`, `CampaignController`, `CreativeController`, `ReportController`, `TenantController`, `AdSlotController` | ✅ 已完成 | 细粒度权限码评估器 `@perm.has(...)` 全面覆盖，数据面显式声明公开契约 |
| T4 | 租户拦截器改造 (严格拒绝、MyBatis-Plus 全表隔离) | 0–1 | 6d | `TenantContextFilter.java`, `TenantContext.java`, `MybatisPlusConfig.java` | ✅ 已完成 | 生产环境下无有效租户严格返回 400，持久层租户字段自动填充与 SQL 级别强制隔离 |
| T5 | Token 轮换、黑名单主动吊销与 jti 唯一性 | 1 | 6d | `RefreshTokenService.java`, `JwtTokenService.java`, `AuthController.java` | ✅ 已完成 | 刷新令牌一次性轮换防窃取，登出/吊销将 jti 纳入黑名单立即作废 |
| T6 | S2S 回传 HMAC-SHA256 签名 / 300s 时间戳 / IP 白名单 | 1 | 4d | `S2sPostbackController.java`, `S2sPostbackAuthAndReplayTest.java` | ✅ 已完成 | 支持广告主 CIDR/通配 IP 过滤，HMAC 签名与 300s 漂移校验杜绝重放伪造 |
| T7 | 不可变复式记账账本 (V23) + 幂等双分录记账 | 1 | 12d | `V23__financial_double_entry_ledger.sql`, `WalletService.java`, `LedgerTransactionMapper`, `LedgerLineMapper` | ✅ 已完成 | 每笔充值/结算严格借贷平衡，(tenant_id, idem_key) 防重保障 |
| T8 | 账本定时对账巡检 + 四眼冲正审批 | 1 | 8d | `WalletService.java`, `LedgerReconciliationScheduler.java`, `BillingController.java` | ✅ 已完成 | 支持每日/按需扫描租户账本核算净差值，P1 告警；经办人与复核人严格双人四眼冲正 |
| T9 | Transactional Outbox + 失败 5 次隔离至死信表 + 重放 | 1 | 6d | `V24__event_outbox_dead_letter.sql`, `JdbcOutboxStore.java`, `OutboxDeadLetterService.java`, `OutboxAdminController.java` | ✅ 已完成 | 消息积压/毒丸消息自动转移死信表解热点，暴露运维审计重放端点 |
| T10 | 模块化集成测试套件与覆盖率检验 | 1 | 8d | 各模块测试套件 (全工程 24 个模块单元/集成测试) | ✅ 已完成 | 全工程 24 模块测试 100% BUILD SUCCESS 绿通 |
| T11 | 四个启动模块 + K8s 物理拆分 + 低延迟 JVM 调优 | 2 | 10d | `platform-app-tracker`, `platform-app-rtb`, `platform-app-admin`, `platform-app-worker`, `k8s/split/` | ✅ 已完成 | 数据面与控制面完全物理拆离，RTB 部署配置 Generational ZGC 参数 |
| T12 | `ExternalCallExecutor` 状态机 + AI 成本日预算护栏 | 2 | 6d | `ExternalCallExecutor.java`, `AiCostGuardService.java`, `CircuitBreakerOpenException` | ✅ 已完成 | 统一熔断器状态机 (CLOSED -> OPEN -> HALF_OPEN)，AI 与支付调用严格禁止重试，租户日预算事前阻断 |
| T13 | 核心业务与高可用 SLO 监控指标收集器 | 2 | 8d | `SloMetricsCollector.java`, `SloMetricsCollectorTest.java` | ✅ 已完成 | 统一注册 RTB P99 竞价延迟、点击跳转延迟、S2S 状态分布、对账差错与 Outbox 死信等指标 |
| T14 | K8s 自动化迁移 Job + 增量脚本核对 | 1 | 3d | `k8s/migration-job.yaml`, `k8s/split/kustomization.yaml` | ✅ 已完成 | 预置 ArgoCD / Helm pre-sync hook Job，业务 Pod 滚动更新前自动执行 Flyway 增量脚本 |

---
**当前成果**：
全项目 24 个 Maven 模块已完成商业级演进核心代码编写与架构重构，测试套件构建全部通过（BUILD SUCCESS），具备高度可观测性、数据面/控制面物理隔离、资金复式记账不可变审计与零信任安全基线。
