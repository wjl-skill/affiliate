# Affiliate Platform Domain Context

本文档只定义业务语言，不描述具体实现。代码、接口和数据库字段应优先使用这些术语。

## 参与方

- **Tenant（租户）**：平台资源和计费边界。所有业务实体和事件都必须归属一个租户。
- **Platform User（平台用户）**：登录平台的自然人或服务账号，通过 RBAC 获得权限。
- **Advertiser（广告主）**：购买媒体流量的一方，拥有账户、活动和预算。
- **Publisher（媒体方）**：提供网站、应用和广告位的一方。
- **Supply Partner（供应合作方）**：代表 SSP、ADX 或媒体网络的连接配置。
- **Demand Partner（需求合作方）**：代表 DSP 或广告主投放系统的连接配置。
- **DMP（数据管理平台）**：管理匿名的第三方/第二方 Cookie、设备 ID 和短生命周期受众，主要用于付费广告定向与 Lookalike 放大。
- **CDP（客户数据平台）**：管理租户第一方实名身份、跨渠道行为和长期客户画像，负责 ID Mapping、同意状态和私域触达。

## 投放对象

- **Campaign（广告活动）**：广告主的预算、目标、投放周期和计费策略集合。
- **Ad Group（广告组）**：活动下共享定向、出价和频控规则的一组广告。
- **Creative（广告素材）**：可审核、可投放的图片、视频、原生或 HTML5 资产。
- **Site/Application（站点/应用）**：Publisher 的流量载体。
- **Ad Slot（广告位）**：站点/应用中的可售库存，包含尺寸、协议、底价和安全属性。

## 交易与事件

- **DSP**：需求方平台，选择符合定向的库存并计算每次曝光出价。
- **SSP**：供应方平台，管理媒体库存并向交易市场发起请求。
- **ADX**：广告交易平台，接收 Bid Request、并行询价、执行竞价和返回 Bid Response。
- **Bid Request（竞价请求）**：SSP 发给 ADX/DSP 的一次曝光机会描述。
- **Bid Response（竞价响应）**：DSP 对一个或多个 Imp 的出价、素材和点击地址。
- **Auction（竞价）**：在单个请求内按规则筛选候选出价并确定胜出结果。
- **Win Notice（胜出通知）**：交易完成后发送给胜出 DSP 的结算和追踪通知。
- **Impression / Click / Conversion**：曝光、点击、转化事实事件，均可重放且带幂等键。

## 资金与分析

- **Budget Reservation（预算预占）**：竞价前原子锁定预算；成交确认或超时释放。
- **Billing Entry（计费分录）**：不可变的借贷明细，以幂等键防止重复记账。
- **Settlement（结算）**：按租户、合作方和账期汇总可结算金额并生成对账差异。
- **Report（报表）**：从事件事实聚合出的可查询指标，不反向修改计费分录。

## 数据身份边界

- **Anonymous Identifier（匿名标识）**：Cookie、设备 ID 等不可直接识别自然人的短期标识，只能进入 DMP 受众。
- **First-party Identifier（第一方标识）**：手机号、微信号、OpenID、会员卡号等由租户合法收集并经同意管理的身份标识，只能进入 CDP。
- **Identity Mapping（身份映射）**：将多个第一方标识合并到一个 Customer Profile；同一租户内必须唯一，冲突时拒绝写入。
- **Customer Profile（客户画像）**：CDP 中长期保存的实名客户聚合视图，支持合并、分群、触达和删除/退出。
- **Audience Segment（受众分群）**：DMP 中可激活的匿名人群，默认 30-90 天有效，过期后不可用于竞价。

## 外部产品

- **Google Ads**：广告主投放和报表 API，使用 `platform-google-ads` 连接器。
- **Google Ad Manager (GAM)**：媒体侧订单、广告位和投放 API，使用 `platform-google-gam` 连接器。

Google Ads 与 GAM 是两个不同产品，凭据、授权范围、速率限制和错误语义不得混用。
