package com.affiliate.platform.domain;

/**
 * 平台通用领域枚举定义集 (Platform Domain Enums)
 * <p>
 * 集中维护广告投放、物料类型、计费模型、审核合规与连接状态的核心枚举。
 */
public final class Enums {
    private Enums() {}

    /**
     * 流量与需求对接方角色类型
     */
    public enum SupplyType {
        /** 需求方平台 (Demand Side Platform) */
        DSP,
        /** 供给方平台 (Supply Side Platform) */
        SSP,
        /** 广告交易平台 (Ad Exchange) */
        ADX
    }

    /**
     * 广告物料表现形态类型
     */
    public enum CreativeType {
        /** 静态/动态图文横幅 (Banner) */
        BANNER,
        /** 视频广告 (Video，如贴片、激励视频) */
        VIDEO,
        /** 信息流原生广告 (Native) */
        NATIVE,
        /** 富媒体与交互式 H5 页面广告 (HTML5) */
        HTML5
    }

    /**
     * 广告物料审核合规生命周期状态
     */
    public enum AuditStatus {
        /** 待审核：新创建或修改后等待安全合规审核 */
        PENDING_REVIEW,
        /** 已审核通过：合规并允许参与 RTB 撮合 */
        APPROVED,
        /** 审核驳回：命中违规策略或尺寸格式不符 */
        REJECTED
    }

    /**
     * 广告商业计费结算模式
     */
    public enum PricingModel {
        /** 按千次展示计费 (Cost Per Mille) */
        CPM,
        /** 按用户单次点击计费 (Cost Per Click) */
        CPC,
        /** 按用户转化行为计费 (Cost Per Action) */
        CPA
    }

    /**
     * 第三方生态或集成连接器在线运行状态
     */
    public enum ConnectionStatus {
        /** 正常启用同步中 */
        ACTIVE,
        /** 暂停同步中 */
        PAUSED,
        /** 凭据失效或网络通讯异常 */
        ERROR
    }
}
