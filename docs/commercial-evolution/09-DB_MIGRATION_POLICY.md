# 数据库迁移规范

> 所属:商业级演化详细设计(阶段 0–2)。总览见 [README](README.md)。


- 版本空洞:当前迁移为 V1–V13、V16–V22,**缺 V14/V15**(`docs/sql` 里有 `14_*`、`15_*`)。需确认是有意跳号还是遗漏;新环境以 `flyway validate` + 全量空库建表测试为准。
- 生产 `FLYWAY_ENABLED` 默认改为独立 **migration Job**(K8s Job,先于 Deployment 发布),应用侧 `validate-on-migrate`。
- 规则:**expand → migrate → contract** 三步分属不同版本;大表变更用 `CREATE INDEX CONCURRENTLY`、分批回填;每个迁移需附回滚说明。
- 分区:`V22` 已含 2026 全年分区,新增 `pg_partman` 或定时任务提前建下一年分区,并加告警(DEFAULT 分区行数 > 0)。

