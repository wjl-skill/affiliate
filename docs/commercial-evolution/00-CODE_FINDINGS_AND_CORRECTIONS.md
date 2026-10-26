# 对上一份报告的修正(读码后)

> 所属:商业级演化详细设计(阶段 0–2)。总览见 [README](README.md)。


| 项 | 修正 |
|---|---|
| 授权 | 并非"几乎没有授权":`SecurityConfiguration` 已有 URL 级规则,`@EnableMethodSecurity` 已开启,`RbacService` 定义了 6 个角色。**缺口是:** 业务接口(billing/budget/creative…)仅 `anyRequest().authenticated()`,没有角色/资源级约束。 |
| 租户隔离 | 已有 `MybatisPlusConfig` 的 `TenantLineInnerInterceptor`。**缺口是:** 无租户上下文时**静默回退 `"public"`**,且仅作用于白名单 `TENANT_TABLES`。异步线程、Kafka 消费者、调度器天然没有 `TenantContext`,会把数据读写到 `public`。 |
| 钱包 | 余额更新用 SQL `atomicRecharge`(原子),流水有 `idempotency_key unique`,账本失败会重新抛出并回滚——**方向正确**。缺口是:`Striped<Lock>` 仅 JVM 内有效;`getOrCreate("public", …)` 硬编码租户;内存回退路径仍存在于生产类中;无对账、无冲正模型。 |
| 其余 | `application-prod.yml` 中 Flyway 默认 `false`;Kafka `trusted.packages: "*"`(反序列化风险);`k8s/secret.yaml` 明文密钥(原结论成立)。 |

