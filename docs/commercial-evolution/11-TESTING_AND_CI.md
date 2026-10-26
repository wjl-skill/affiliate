# 测试与 CI

> 所属:商业级演化详细设计(阶段 0–2)。总览见 [README](README.md)。


| 层 | 内容 | 门禁 |
|---|---|---|
| 单元 | 账本借贷平衡、状态机、算法 | 行覆盖:资金/归因 ≥85%,其余 ≥70%(JaCoCo) |
| 架构 | ArchUnit:端点必有权限、模块依赖、禁止 `catch(Exception)` 吞异常 | 失败即阻断 |
| 集成 | Testcontainers(PG/Redis/Kafka/ClickHouse):幂等并发(100 线程同键)、多 Pod 并发扣款、租户越权、Outbox 故障恢复 | PR 必跑 |
| 契约 | OpenAPI diff(破坏性变更阻断);Kafka schema 兼容 | PR |
| 性能 | k6 夜间对真实依赖压测,趋势入库,回归 >10% 阻断发布 | nightly |
| 混沌 | Redis/Kafka/PG 主从故障注入(Chaos Mesh) | 每月演练 |
- CI 新增:gitleaks、Trivy(镜像+依赖)、OWASP dependency-check、SBOM(CycloneDX)、镜像签名(cosign)、`flyway validate` + 空库迁移测试。
- 仓库卫生:`.gitignore` 加 `target/`、`.idea/`;清理已提交产物。

