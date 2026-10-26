# 阶段 3/4 预留点

> 所属:商业级演化详细设计(阶段 0–2)。总览见 [README](README.md)。


- 计量:`ledger_line` 之上加 `usage_event`(click/impression/ai_call)与套餐/发票表;Kafka → ClickHouse 聚合 → 出账。
- 合规:同意状态与数据主体请求走独立 `privacy` 模块,PII 字段级加密(信封加密,KMS)。
- 模型:特征平台与实验平台以事件管线为输入,接口通过 `BidStrategy`/`TdsRouter` 既有扩展点接入。

