# 可观测性与 SLO

> 所属:商业级演化详细设计(阶段 0–2)。总览见 [README](README.md)。


| SLO | 目标 | 指标 |
|---|---|---|
| RTB 可用/延迟 | 99.95% / p99<80ms | `rtb_latency_seconds`, `rtb_nobid_ratio` |
| 点击跳转 | p99<50ms | `click_redirect_seconds` |
| Postback 不丢 | 丢失<0.01% | `postback_received − conversion_persisted` |
| 账务正确 | 对账差异=0 | `ledger_reconcile_diff` |
| Outbox 延迟 | p99<5s | `outbox_lag_seconds` |
- 日志统一 JSON,含 `trace_id/tenant_id/user_id`(PII 脱敏过滤器);告警基于错误预算燃烧率(多窗口)。
- 现有三块 Grafana 看板补充:账本对账、Outbox/DLT、租户维度成本。

