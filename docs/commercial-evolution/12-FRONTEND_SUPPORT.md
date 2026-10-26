# 前端(`platform-frontend`)配套

> 所属:商业级演化详细设计(阶段 0–2)。总览见 [README](README.md)。


- 按权限码渲染菜单/按钮(`menu.ts` 对接 `perms`);Token 内存存储 + HttpOnly Refresh Cookie;401 自动刷新队列。
- 新增:账本/对账看板、DLT 重放页、审批中心(打款/冲正)、租户成本页。
- 引入 Vitest + Playwright 冒烟(登录、充值、审批、报表)。

