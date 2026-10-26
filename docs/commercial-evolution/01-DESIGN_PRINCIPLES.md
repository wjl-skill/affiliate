# 设计原则

> 所属:商业级演化详细设计(阶段 0–2)。总览见 [README](README.md)。


1. **默认拒绝**:无租户、无权限、无幂等键的请求一律失败,不做静默兜底。
2. **单一事实源**:资金以 PostgreSQL 账本为准,Redis/内存只做缓存,可由账本重建。
3. **生产代码不含内存回退**:内存实现移入 `src/test` 或 `local` profile,类名带 `InMemory`/`Fake`。
4. **所有变更可回滚、可灰度**:feature flag + expand/contract 迁移。

