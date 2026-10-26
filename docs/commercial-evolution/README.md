# 商业级演化详细设计 (阶段 0–2)


> 范围:对应上一份路线图的阶段 0(安全底线)、阶段 1(资金与数据正确性)、阶段 2(服务拆分与可靠性)。
> 依据:本次对 `platform-auth`、`platform-billing`、`platform-event`、`platform-infrastructure`、`k8s`、`application-prod.yml` 的代码阅读。阶段 3/4(SaaS 计费、合规、模型)只在文末列出接口预留。


## 文档索引

| # | 文档 |
|---|---|
| 0 | [对上一份报告的修正(读码后)](00-CODE_FINDINGS_AND_CORRECTIONS.md) |
| 1 | [设计原则](01-DESIGN_PRINCIPLES.md) |
| 2 | [密钥与配置治理(Phase 0)](02-SECRETS_AND_CONFIG_GOVERNANCE.md) |
| 3 | [认证与授权(Phase 0)](03-AUTHN_AUTHZ.md) |
| 4 | [租户隔离(Phase 0 → 1)](04-TENANT_ISOLATION.md) |
| 5 | [账本与资金(Phase 1,核心)](05-LEDGER_AND_FUNDS.md) |
| 6 | [事件管线(Phase 1)](06-EVENT_PIPELINE.md) |
| 7 | [服务拆分(Phase 2)](07-SERVICE_SPLIT.md) |
| 8 | [外部依赖韧性与成本护栏(Phase 2)](08-EXTERNAL_RESILIENCE_AND_COST_GUARD.md) |
| 9 | [数据库迁移规范](09-DB_MIGRATION_POLICY.md) |
| 10 | [可观测性与 SLO](10-OBSERVABILITY_AND_SLO.md) |
| 11 | [测试与 CI](11-TESTING_AND_CI.md) |
| 12 | [前端(`platform-frontend`)配套](12-FRONTEND_SUPPORT.md) |
| 13 | [实施任务分解](13-IMPLEMENTATION_TASKS.md) |
| 14 | [风险与回滚](14-RISKS_AND_ROLLBACK.md) |
| 15 | [阶段 3/4 预留点](15-PHASE3_4_HOOKS.md) |
| 16 | [待确认](16-OPEN_QUESTIONS.md) |

配套:[演化路线图与现状分析](EVOLUTION_PLAN_OVERVIEW.md)
