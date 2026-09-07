package com.affiliate.platform.dmp;

import java.util.HashSet;
import java.util.Set;

/**
 * 人群分群重叠度与覆盖分析服务 (Segment Overlap Analytics Service)
 * <p>
 * 分析多个人群包之间的交集、并集和重叠度比例，为媒介策略制定提供依据。
 */
public class SegmentAnalyticsService {

    public record OverlapResult(
            int segmentASize,
            int segmentBSize,
            int intersectionSize,
            int unionSize,
            double overlapRatio
    ) {}

    /**
     * 计算两个人群集合的重叠分析指标
     */
    public OverlapResult analyze(Set<String> setA, Set<String> setB) {
        if (setA == null) setA = Set.of();
        if (setB == null) setB = Set.of();

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        int minSize = Math.min(setA.size(), setB.size());
        double overlapRatio = minSize == 0 ? 0.0 : (double) intersection.size() / minSize;

        return new OverlapResult(setA.size(), setB.size(), intersection.size(), union.size(), overlapRatio);
    }
}
