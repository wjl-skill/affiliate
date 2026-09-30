package com.affiliate.platform.rtb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

/**
 * OpenRTB 2.5 工业界标准数据模型协议定义 (OpenRTB 2.5 Protocol Definitions)
 * <p>
 * 遵循 IAB OpenRTB 2.5 协议规范，定义实时竞价请求、曝光位、网站、设备、用户、PMP 私有交易及出价响应。
 */
public final class OpenRtb {
    private OpenRtb() {}

    /**
     * 实时竞价请求顶级对象 (OpenRTB BidRequest)
     *
     * @param id     竞价请求全局唯一追踪 ID
     * @param imp    本次请求包含的曝光位机会列表（至少 1 个）
     * @param site   发起请求的发布商网站信息（App 场景时对应 app）
     * @param device 客户端用户设备硬件与环境信息
     * @param user   目标受众用户信息
     * @param tmax   最大超时时限（毫秒，默认 20ms）
     */
    public record BidRequest(
            @NotBlank String id,
            @NotNull List<Imp> imp,
            Site site,
            Device device,
            User user,
            Integer tmax
    ) {
        /**
         * 兼容性构造器（默认 20ms 超时预算）
         */
        public BidRequest(String id, List<Imp> imp, Site site, Device device, User user) {
            this(id, imp, site, device, user, 20);
        }
    }

    /**
     * 单个广告展示机会曝光对象 (Impression)
     *
     * @param id          曝光位标识
     * @param banner      横幅广告规格
     * @param video       视频广告规格
     * @param bidfloor    媒体设置的最低出价底价
     * @param bidfloorcur 底价结算货币代码（如 "USD"）
     * @param pmp         私有交易市场 (PMP) 协议配置
     */
    public record Imp(
            @NotBlank String id,
            Banner banner,
            Video video,
            double bidfloor,
            String bidfloorcur,
            Pmp pmp
    ) {
        // 向后兼容构造器
        public Imp(String id, Banner banner, Video video, double bidfloor, String bidfloorcur) {
            this(id, banner, video, bidfloor, bidfloorcur, null);
        }
    }

    /**
     * 私有交易市场协议 (Private Marketplace)
     *
     * @param private_auction 1 为仅限私有竞价，0 为允许公开竞价
     * @param deals           私有交易 Deal 列表
     */
    public record Pmp(Integer private_auction, List<Deal> deals) {
        public Pmp {
            deals = deals == null ? List.of() : List.copyOf(deals);
        }
    }

    /**
     * 私有交易单个协议条款 (PMP Deal)
     *
     * @param id          Deal 全局唯一标识
     * @param bidfloor    Deal 专属保留底价
     * @param bidfloorcur 底价货币
     * @param wseat       允许参与的 DSP 席位白名单
     * @param at          拍卖类型（1 为第一价，2 为第二价，3 为固定价）
     */
    public record Deal(String id, double bidfloor, String bidfloorcur, List<String> wseat, Integer at) {
        public Deal {
            wseat = wseat == null ? List.of() : List.copyOf(wseat);
        }
    }

    /**
     * 横幅广告物料要求
     */
    public record Banner(int w, int h, List<Integer> api) {}

    /**
     * 视频广告播放要求
     */
    public record Video(int w, int h, int minduration, int maxduration, List<Integer> mimes) {}

    /**
     * 媒体站点上下文
     */
    public record Site(String domain, String page, Content content) {}

    /**
     * 页面内容特征元数据
     */
    public record Content(String language, List<String> keywords) {}

    /**
     * 客户端硬件与网络设备上下文
     */
    public record Device(String ua, String ip, int devicetype, String geo) {}

    /**
     * 目标受众用户画像 (支持 CDP 受众标签注入)
     *
     * @param id       媒体侧用户 ID（Cookie ID）
     * @param buyeruid DSP 侧同步匹配后的买方受众 ID
     * @param segments CDP/DMP 注入的一方受众分群与标签集合
     */
    public record User(String id, String buyeruid, Set<String> segments) {
        // 向后兼容 2 参数构造器
        public User(String id, String buyeruid) {
            this(id, buyeruid, Set.of());
        }

        public User {
            segments = segments == null ? Set.of() : Set.copyOf(segments);
        }
    }

    /**
     * 竞价响应顶级对象 (OpenRTB BidResponse)
     */
    public record BidResponse(String id, List<SeatBid> seatbid, String cur) {}

    /**
     * 买方出价席位实体
     */
    public record SeatBid(List<Bid> bid, String seat) {}

    /**
     * 单个曝光位的出价明细实体
     *
     * @param id      本次出价标识
     * @param impid   对应的曝光位 ID (imp.id)
     * @param price   买方出价金额 (CPM)
     * @param adm     广告渲染代码 (HTML / JS / VAST XML)
     * @param adomain 广告主的落地页主域名（用于行业排他与合规审查）
     * @param nurl    胜出通知回调 URL (Win Notice URL，携带防篡改 Token)
     * @param crid    投放的素材唯一标识 ID (creative.id)
     * @param dealid  买方引用的私有交易 Deal ID (可选)
     */
    public record Bid(
            String id,
            String impid,
            double price,
            String adm,
            String adomain,
            String nurl,
            String crid,
            String dealid
    ) {
        // 向后兼容 7 参数构造器
        public Bid(String id, String impid, double price, String adm, String adomain, String nurl, String crid) {
            this(id, impid, price, adm, adomain, nurl, crid, null);
        }
    }
}
