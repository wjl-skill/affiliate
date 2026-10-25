package com.affiliate.platform.affiliate.domain;

/**
 * 存储桶 Parquet 用户设备画像记录 (Supplement User Device Profile)
 * <p>
 * 对应 Spark Parquet 用户信息文件结构：
 * - `id`: 记录或设备主键 (如 9DB378C6-22B5-407B-AAC5-8D47F7898E49)
 * - `gadvid`: Google Advertising ID (GAID, Android 端广告标识)
 * - `idfa`: Apple iOS IDFA (iOS 端广告标识)
 * - `ip`: 客户端真实 IP 地址 (如 27.52.129.115)
 * - `ua`: 客户端真实 User-Agent
 * - `os`: 操作系统标识 (如 ios / android)
 * - `ds_publisher_id`: 下游媒体 Publisher ID (如 56cff4d39deefe246000001d)
 * - `ds_adx`: 下游 ADX 交易平台标识 (如 mvungle / inmobi / topon / chartboost)
 * - `s`: 流量源与地区系统标签 (如 ym_tw_ios)
 * - `m`: 流量分层权重/模型标识 (如 5 / 6 / 7)
 */
public record SupplementUserProfile(
        String id,
        String gadvid,
        String idfa,
        String ip,
        String ua,
        String os,
        String dsPublisherId,
        String dsAdx,
        String s,
        Integer m
) {
    /**
     * 获取优先设备广告号 (iOS 优先取 idfa，Android 优先取 gadvid，兜底取 id)
     */
    public String primaryDeviceId() {
        if (idfa != null && !idfa.isBlank()) {
            return idfa;
        }
        if (gadvid != null && !gadvid.isBlank()) {
            return gadvid;
        }
        return id != null ? id : "";
    }

    /**
     * 从流量源标签 `s` (如 ym_tw_ios / us_android) 推断国家代码，默认 TW/US
     */
    public String inferCountry(String fallbackCountry) {
        if (s != null && !s.isBlank()) {
            String[] parts = s.toLowerCase().split("[_\\-]");
            for (String p : parts) {
                if (p.length() == 2 && !p.equals("ym") && !p.equals("ds") && !p.equals("os")) {
                    return p.toUpperCase();
                }
            }
        }
        return (fallbackCountry != null && !fallbackCountry.isBlank()) ? fallbackCountry.toUpperCase() : "TW";
    }
}
