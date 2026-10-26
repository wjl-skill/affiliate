# 认证与授权(Phase 0)

> 所属:商业级演化详细设计(阶段 0–2)。总览见 [README](README.md)。


### 3.1 令牌模型
| 令牌 | 载体 | TTL | 说明 |
|---|---|---|---|
| Access | JWT(`sub, tenant_id, roles, perms_ver, jti, kid`) | 15 min | 无状态 |
| Refresh | 不透明随机串,库内存哈希 | 7 d | 单次使用轮换,检测重放即吊销整族 |
| API Key | `ak_<prefix>.<secret>`,库内存 Argon2 哈希 | 可配 | 绑定租户、作用域、IP 白名单、限流 |

- 吊销:Redis `revoked:jti:{jti}`(TTL=剩余寿命);`perms_ver` 变化时旧令牌在下次校验被拒。
- 登录:失败计数 + 指数退避,5 次锁 15 分钟;密码 Argon2id;管理员强制 TOTP(后续)。

### 3.2 权限模型(RBAC + 资源作用域)
权限码规范:`<resource>:<action>`,如 `wallet:recharge`、`payout:approve`、`offer:write`、`report:read`。

| 角色 | 关键权限 |
|---|---|
| SUPER_ADMIN | 全部(平台级,跨租户需审计) |
| TENANT_ADMIN | 租户内全部,不含 `payout:approve` |
| FINANCE | `wallet:*`、`payout:*`、`billing:read` |
| TRAFFICKER | `campaign:*`、`offer:*`、`creative:submit`、`report:read` |
| AUDITOR | `creative:review`、`audit:read` |
| VIEWER | `*:read`(不含 wallet/payout 明细) |

实现:
- `@PreAuthorize("@perm.has('wallet:recharge')")`,`perm` bean 读取 JWT 的 `roles` 并查缓存的 `RoleDefinition`(`RbacService` 已有雏形,补齐权限码映射)。
- **覆盖保障**:ArchUnit 测试——所有 `@RestController` 的每个 handler 必须带 `@PreAuthorize` 或显式 `@PublicEndpoint`,否则 CI 失败。
- **四眼原则**:`payout:approve` 与 `payout:create` 不得同一用户;充值超阈值需二次审批。
- 审计:`SecurityAuditService` 扩展为 AOP 切面,记录 who/tenant/action/resource/before-after/ip/trace_id;审计表只追加(DB 权限禁 UPDATE/DELETE)。

### 3.3 公开入口加固(`/affiliate/**` 目前整体 `permitAll`)
- 仅放行 `GET /affiliate/click`、`/affiliate/postback`;其余管理路径(如 `AffiliateAdminController`)需认证。
- Postback 校验:HMAC 签名(`HmacTokenService` 已有)+ 时间戳窗口 ±5 min + 来源 IP 白名单(按 Partner 配置)+ 速率限制。

