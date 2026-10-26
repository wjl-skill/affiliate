# 租户隔离(Phase 0 → 1)

> 所属:商业级演化详细设计(阶段 0–2)。总览见 [README](README.md)。


### 4.1 修改点
```java
// MybatisPlusConfig.TenantLineHandler(目标行为)
getTenantId():
  tenant = TenantContext.get();
  if (tenant == null) throw new MissingTenantException();   // 不再回退 "public"
ignoreTable(name):  // 默认 false;改为 *白名单忽略* 全局表,而非白名单 *启用* 租户表
  return GLOBAL_TABLES.contains(name);
```
- 由"白名单启用"改为"**全表启用 + 全局表豁免**"(如 `sys_config`、`ai_model_config`),新增表默认隔离。
- 平台任务需跨租户时显式 `TenantContext.runAs(tenantId, …)` 或 `TenantContext.runAsSystem(reason, …)`(后者写审计)。

### 4.2 上下文传播
| 场景 | 方案 |
|---|---|
| HTTP | `TenantContextFilter` 仅信任 JWT claim;`X-Tenant-ID` 与 claim 不一致 → 403(目前 header 可作来源,需收敛) |
| 异步线程池 | `TaskDecorator` 复制 `TenantContext` + MDC |
| Kafka | 生产者写 `tenant_id` header;消费者拦截器置入上下文;缺失 → 进 DLQ |
| 调度任务 | 按租户循环 `runAs`;分布式锁 key 带 tenant |
| Redis key | 统一 `KeyBuilder.of(tenant, domain, id)`,禁止手拼 |

### 4.3 纵深防御
- PostgreSQL **Row Level Security**:核心表启用 RLS,`SET LOCAL app.tenant_id` 由连接拦截器在事务开头设置;应用层 Bug 时 DB 仍兜底。
- 自动化:集成测试对每个 Mapper 以租户 A 写入、租户 B 读取,必须为空(扫描 `TENANT` 表自动生成用例)。

