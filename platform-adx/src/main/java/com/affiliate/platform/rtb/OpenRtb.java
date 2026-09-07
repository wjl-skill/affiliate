package com.affiliate.platform.rtb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * OpenRTB 2.5 工业界标准数据模型协议定义 (OpenRTB 2.5 Protocol Definitions)
 * <p>
 * 遵循 IAB OpenRTB 2.5 协议规范，定义实时竞价请求、曝光位、网站、设备、用户及出价响应。
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
     */
    public record Imp(@NotBlank String id, Banner banner, Video video, double bidfloor, String bidfloorcur) {}

    /**
     * 横幅广告物料要求
     *
     * @param w   期望宽度像素
     * @param h   期望高度像素
     * @param api 支持的交互式 API 框架列表（如 VPAID, MRAID）
     */
    public record Banner(int w, int h, List<Integer> api) {}

    /**
     * 视频广告播放要求
     *
     * @param w           视频宽度像素
     * @param h           视频高度像素
     * @param minduration 视频最短播放时长（秒）
     * @param maxduration 视频最长播放时长（秒）
     * @param mimes       支持的视频多媒体格式编码
     */
    public record Video(int w, int h, int minduration, int maxduration, List<Integer> mimes) {}

    /**
     * 媒体站点上下文
     *
     * @param domain  站点顶级域名
     * @param page    当前展示广告的具体页面 URL
     * @param content 页面包含的内容元数据
     */
    public record Site(String domain, String page, Content content) {}

    /**
     * 页面内容特征元数据
     *
     * @param language 页面语言代码（如 "zh", "en"）
     * @param keywords 页面关键词标签列表
     */
    public record Content(String language, List<String> keywords) {}

    /**
     * 客户端硬件与网络设备上下文
     *
     * @param ua         客户端浏览器 User-Agent 字符串
     * @param ip         客户端公网 IPv4 或 IPv6 地址
     * @param devicetype 设备形态类型编码（1-手机, 2-PC, 4-平板, 5-智能电视）
     * @param geo        地理位置编码（国家/省市代码）
     */
    public record Device(String ua, String ip, int devicetype, String geo) {}

    /**
     * 目标受众用户画像
     *
     * @param id       媒体侧用户 ID（Cookie ID）
     * @param buyeruid DSP 侧同步匹配后的买方受众 ID
     */
    public record User(String id, String buyeruid) {}

    /**
     * 竞价响应顶级对象 (OpenRTB BidResponse)
     *
     * @param id      对应的竞价请求 ID
     * @param seatbid 买方出价席位列表
     * @param cur     出价货币代码（通常为 "USD"）
     */
    public record BidResponse(String id, List<SeatBid> seatbid, String cur) {}

    /**
     * 买方出价席位实体
     *
     * @param bid  席位包含的具体出价列表
     * @param seat 买方席位或账户 ID
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
     */
    public record Bid(String id, String impid, double price, String adm, String adomain, String nurl, String crid) {}
}
