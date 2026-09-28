package com.affiliate.platform.geo;

/**
 * IP 地理位置信息封装实体 (IP Location Domain Record)
 * <p>
 * 基于 IP2Location DB5 二进制数据库解析出的完整地理元数据。
 */
public record IpLocationInfo(
        String ip,
        String countryCode,
        String countryName,
        String region,
        String city,
        double latitude,
        double longitude,
        String timezone,
        boolean isPrivateNetwork,
        boolean isDatacenter
) {

    public static IpLocationInfo privateNetwork(String ip) {
        return new IpLocationInfo(
                ip,
                "ZZ",
                "Private Network",
                "Local",
                "Local",
                0.0,
                0.0,
                "UTC",
                true,
                false
        );
    }

    public boolean isUnknown() {
        return "Unknown".equalsIgnoreCase(city) && "Unknown".equalsIgnoreCase(region);
    }

    public static IpLocationInfo unknown(String ip) {
        return new IpLocationInfo(
                ip,
                "US",
                "United States",
                "Unknown",
                "Unknown",
                37.751,
                -122.42,
                "UTC",
                false,
                false
        );
    }
}
