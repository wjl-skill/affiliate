# 服务拆分(Phase 2)

> 所属:商业级演化详细设计(阶段 0–2)。总览见 [README](README.md)。


### 7.1 目标部署单元
| 单元 | 包含模块 | 特性 | 伸缩 |
|---|---|---|---|
| `tracker` | affiliate(click/postback/skan)、event(生产)、infrastructure(redis) | 无状态、无 PG 同步写热路径、返回前仅写 Redis+Kafka | CPU/RPS HPA |
| `rtb` | adx、dsp(竞价)、budget(Redis)、ssp | 内存索引快照、ZGC、不依赖 PG | RPS/延迟 HPA |
| `admin` | auth、tenant、dsp/ssp 管理、reporting、billing API | 连 PG | 副本 2–4 |
| `worker` | outbox relay、结算、对账、creative 轮询、补量任务 | 无入口流量,领导选举/SKIP LOCKED | 队列深度(KEDA) |

- 做法:新增 `platform-app-tracker/rtb/admin/worker` 四个薄 Spring Boot 启动模块(只含 `@SpringBootApplication` + 依赖选择),保留现有 `platform-api` 作为本地全量模式。**业务模块不动**,风险最低。
- 模块依赖治理:ArchUnit 规则禁止 `adx/dsp/affiliate` 之间互相直接引用 Service,跨域走接口或事件。
- K8s:现有 `deployment-dataplane/controlplane` 拆为 4 个 Deployment;每个独立 PDB、HPA、资源与探针(readiness 含 Redis/Kafka 依赖,liveness 不含)。

### 7.2 数据面性能预算(分段,用于端到端压测验收)
| 段 | 预算 |
|---|---|
| 网关/TLS | 2 ms |
| 反序列化 + 校验 | 3 ms |
| 倒排索引召回 + 打分 | 5 ms |
| Redis 预算预占(Lua) | 3 ms |
| 竞价方 fan-out(异步、超时 40ms) | 40 ms |
| 清算 + 响应 | 3 ms |
| **p99 合计** | **< 80 ms** |
并保留已有 `RtbAdaptiveLoadShedder`:超过阈值返回 204 No Bid 而非排队。

