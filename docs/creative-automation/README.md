# Affiliate 素材自动化 (Creative Automation) 详细设计方案文档集

> 本文档集系统梳理并沉淀了面向程序化广告（RTB/DSP）与网盟效果营销（Affiliate Marketing）的工业级素材自动化生产、处理、打标与投放闭环设计方案。
> 参考基线：深度复用并演进项目在多模型接入、长视频工作台、画幅对齐拼接、动态基准线质量打标与跨媒体自动化投放等成熟实践。

---

## 📚 文档全景导航

| 序号 | 模块与专题 | 文档链接 | 核心设计要点 |
|---|---|---|---|
| **00** | **全局架构总纲** | [CREATIVE_AUTOMATION_SYSTEM_DESIGN.md](../CREATIVE_AUTOMATION_SYSTEM_DESIGN.md) | 全链路数据闭环全景、架构分层、12 张生产表 DDL 与全套 RESTful API 契约 |
| **01** | **AI 视频多模型引擎** | [01-AI_VIDEO_MULTI_MODEL_ENGINE.md](01-AI_VIDEO_MULTI_MODEL_ENGINE.md) | `Generator` + `Provider` + `ModelMeta` 三层解耦、动态表单驱动、异步生命周期与双重转存 |
| **02** | **AI 长视频工作台流水线** | [02-LONG_VIDEO_STUDIO_PIPELINE.md](02-LONG_VIDEO_STUDIO_PIPELINE.md) | 7 阶段工程流水线、6 套行业分镜 Prompt 管线、边界关键帧法 ($K_0 \sim K_n$)、数字人驱动与剪映草稿微秒级导出 |
| **03** | **视频智能剪辑与混剪中心** | [03-VIDEO_PROCESSING_AND_AUTO_STITCH.md](03-VIDEO_PROCESSING_AND_AUTO_STITCH.md) | 媒体探针 MediaProbe、画幅自适应硬拼接 (Auto-Stitch)、5 大原子算子、定时批量混剪与磁盘安全 |
| **04** | **素材效果打标与动态基准线** | [04-MATERIAL_QUALITY_TAGGING_AND_BENCHMARK.md](04-MATERIAL_QUALITY_TAGGING_AND_BENCHMARK.md) | 7 天滑动窗口大盘基准线 ETL SQL、三重有效样本保护门槛、6 级短路判定状态机与广告名称严格等值匹配 |
| **05** | **跨平台自动化建广与业务赋能** | [05-AD_AUTOMATION_AND_LEARNING_PHASE.md](05-AD_AUTOMATION_AND_LEARNING_PHASE.md) | Meta/TikTok 批量自动化建广、广告学习期监控与熔断、DSP 明星素材出价赋能与 SmartLink EPC 权重优化 |
| **06** | **AI 算力计量与财务分账** | [06-AI_BILLING_AND_SETTLEMENT.md](06-AI_BILLING_AND_SETTLEMENT.md) | 视频时长/Token 计量、对接 `platform-billing` 虚拟钱包预占/实扣、部门分账报表与 AI 投产比 (ROI) 核算 |
| **DDL**| **独立模块建表与索引脚本** | [16_platform_creative_automation.sql](../sql/16_platform_creative_automation.sql) | 12 张核心业务表 PostgreSQL 14+ 完整 DDL、外键约束与复合索引 |

---

## 🏗️ 架构拓扑全景

```mermaid
flowchart TD
    subgraph S1["1. 创意摄取与衍生 (Ingestion & Prompt)"]
        A1["Brief 创意文案 / 商品链接"] --> B["LLM 创意拆解器"]
        A2["Spy 竞品爆款素材库"] --> A3["抽帧与 Prompt 逆向"] --> B
    end

    subgraph S2["2. AI 多模态生成矩阵 (Generation Matrix)"]
        B --> C1["角色 / 产品资产生成 (SD/Flux/MJ)"]
        B --> C2["分镜规划与边界关键帧 (K0~Kn)"]
        C2 --> D1["多厂商视频生成器 (Seedance / Wan / HappyHorse)"]
        B --> D2["数字人与语音驱动 (OmniHuman 1.5 + 豆包TTS)"]
    end

    subgraph S3["3. 视频智能剪辑与后处理 (Video Processing Hub)"]
        D1 --> E["媒体探针 (FFprobe)"]
        D2 --> E
        E --> F1["画幅自适应对齐与硬拼接 (Auto-Stitch)"]
        E --> F2["去水印 / 智能字幕 / CTA动效贴片"]
        F1 --> G1["衍生变体物料库 (千稿千面)"]
        F2 --> G1
        F1 --> G2["剪映草稿工程 (CapCut Draft)"]
    end

    subgraph S4["4. 跨平台自动化建广与投放 (Auto Delivery)"]
        G1 --> H["跨渠道投放策略器 (Meta / TikTok / Google)"]
        H --> I["自动化批量建广 (Campaign/AdSet/Ad)"]
        I --> J["广告学习期 (Learning Phase) 监控熔断"]
    end

    subgraph S5["5. 数据闭环与智能质量打标 (Feedback & Tagging)"]
        I --> K["三方投放消耗与归因事实 (Clicks / Installs / ROAS)"]
        K --> L["7天滑动窗口动态大盘基准线 (Benchmark P75/P25)"]
        L --> M["6级严格质量判定状态机 (STAR/POTENTIAL/BLACKHOLE...)"]
        M -->|爆款素材自动再衍生| S1
        M -->|优质素材赋能 DSP 竞价与 SmartLink| S4
    end

    subgraph S6["6. 财务与算力分账 (Billing & Settlement)"]
        D1 --> N["Token / 视频时长计费分录"]
        D2 --> N
        N --> O["平台虚拟钱包预占/实扣 (platform-billing)"]
    end
```

---

## 🛠️ 核心技术栈与规范

- **核心语言**：Java 21（全面采用 Virtual Threads 虚拟线程调度高 I/O 阻塞轮询任务）
- **核心框架**：Spring Boot 3.4.x、Spring Security、Flyway
- **数据库**：PostgreSQL 14+（行级多租户隔离 `tenant_id`、JSONB 半结构化存储、百分位窗口函数 `PERCENTILE_CONT`）
- **缓存与队列**：Redis 7.x（分布式令牌桶限流、任务去重锁）、Kafka（可靠事件驱动 Outbox 模式）
- **多媒体处理**：FFmpeg 6.x CLI、JavaCV (FFmpegFrameGrabber/Recorder)、Libass（字幕压制）、Delogo（去水印）
- **剪辑生态集成**：CapCut-Mate（剪映草稿生成 API）、字节跳动火山引擎（Seedance 2.0 / OmniHuman 1.5 / 豆包 TTS 2.0）、阿里百炼 DashScope（Wan 2.1 / HappyHorse）
