package com.affiliate.platform.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IpLocationResolverTest {

    @Test
    void testResolvePublicIpv4() {
        IpLocationInfo usInfo = IpLocationResolver.resolve("8.8.8.8");
        assertNotNull(usInfo);
        assertEquals("US", usInfo.countryCode());
        assertEquals("United States of America", usInfo.countryName());
        assertFalse(usInfo.isPrivateNetwork());

        IpLocationInfo cnInfo = IpLocationResolver.resolve("114.114.114.114");
        assertNotNull(cnInfo);
        assertEquals("CN", cnInfo.countryCode());
        assertEquals("China", cnInfo.countryName());
        assertFalse(cnInfo.isPrivateNetwork());

        IpLocationInfo gbInfo = IpLocationResolver.resolve("81.2.69.142");
        assertNotNull(gbInfo);
        assertEquals("GB", gbInfo.countryCode());

        IpLocationInfo jpInfo = IpLocationResolver.resolve("133.20.10.5");
        assertNotNull(jpInfo);
        assertEquals("JP", jpInfo.countryCode());

        IpLocationInfo deInfo = IpLocationResolver.resolve("141.50.20.10");
        assertNotNull(deInfo);
        assertEquals("DE", deInfo.countryCode());
    }

    @Test
    void testResolvePublicIpv6() {
        IpLocationInfo ipv6Info = IpLocationResolver.resolve("2001:4860:4860::8888");
        assertNotNull(ipv6Info);
        assertEquals("US", ipv6Info.countryCode());
        assertFalse(ipv6Info.isPrivateNetwork());
    }

    @Test
    void testResolvePrivateIp() {
        IpLocationInfo loopback = IpLocationResolver.resolve("127.0.0.1");
        assertNotNull(loopback);
        assertTrue(loopback.isPrivateNetwork());
        assertEquals("ZZ", loopback.countryCode());

        IpLocationInfo lan = IpLocationResolver.resolve("192.168.1.100");
        assertNotNull(lan);
        assertTrue(lan.isPrivateNetwork());
    }

    @Test
    void testGetCountryCode() {
        assertEquals("US", IpLocationResolver.getCountryCode("8.8.8.8"));
        assertEquals("CN", IpLocationResolver.getCountryCode("114.114.114.114"));
    }

    @Test
    void testQueryLatency() {
        // 预热
        IpLocationResolver.resolve("8.8.8.8");

        long start = System.nanoTime();
        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            IpLocationResolver.resolve("8.8.8.8");
        }
        long durationNanos = System.nanoTime() - start;
        double avgMicros = (double) durationNanos / iterations / 1000.0;

        // 验证平均耗时远低于 1ms (通常在 1~20 微秒)
        assertTrue(avgMicros < 1000.0, "Average lookup latency should be sub-millisecond, actual: " + avgMicros + " µs");
    }
}
