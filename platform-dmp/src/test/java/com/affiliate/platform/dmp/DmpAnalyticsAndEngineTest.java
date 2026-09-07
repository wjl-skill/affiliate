package com.affiliate.platform.dmp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DmpAnalyticsAndEngineTest {

    @Test
    void dynamicSegmentEngineEvaluation() {
        DynamicSegmentEngine engine = new DynamicSegmentEngine();

        List<DynamicSegmentEngine.Rule> rules = List.of(
                new DynamicSegmentEngine.Rule("category", DynamicSegmentEngine.Operator.EQUALS, "electronics", null),
                new DynamicSegmentEngine.Rule("age", DynamicSegmentEngine.Operator.GREATER_EQUAL, 18, null),
                new DynamicSegmentEngine.Rule("cart", DynamicSegmentEngine.Operator.CONTAINS, "phone", null)
        );

        Map<String, Object> matchingUser = Map.of(
                "category", "electronics",
                "age", 25,
                "cart", "smartphone_case"
        );
        assertTrue(engine.evaluate(matchingUser, rules));

        Map<String, Object> underAgeUser = Map.of(
                "category", "electronics",
                "age", 16,
                "cart", "smartphone_case"
        );
        assertFalse(engine.evaluate(underAgeUser, rules));
    }

    @Test
    void lookalikeExpansionWithJaccardSimilarity() {
        LookalikeExpansionService service = new LookalikeExpansionService();

        Set<String> seedTags = Set.of("tech", "gadgets", "gamer", "crypto");

        List<LookalikeExpansionService.CandidateUser> candidates = List.of(
                new LookalikeExpansionService.CandidateUser("u1", Set.of("tech", "gadgets", "gamer", "crypto")), // 4/4 = 1.0
                new LookalikeExpansionService.CandidateUser("u2", Set.of("tech", "gadgets", "fashion")),         // 2/5 = 0.4
                new LookalikeExpansionService.CandidateUser("u3", Set.of("gardening", "cooking")),              // 0.0
                new LookalikeExpansionService.CandidateUser("u4", Set.of("tech", "gadgets", "gamer"))           // 3/4 = 0.75
        );

        List<LookalikeExpansionService.ScoredUser> expanded = service.expand(seedTags, candidates, 0.4, 10);
        assertEquals(3, expanded.size());
        assertEquals("u1", expanded.get(0).userId());
        assertEquals(1.0, expanded.get(0).similarityScore(), 0.001);
        assertEquals("u4", expanded.get(1).userId());
        assertEquals(0.75, expanded.get(1).similarityScore(), 0.001);
        assertEquals("u2", expanded.get(2).userId());
    }

    @Test
    void segmentOverlapAnalytics() {
        SegmentAnalyticsService analytics = new SegmentAnalyticsService();

        Set<String> setA = Set.of("u1", "u2", "u3", "u4");
        Set<String> setB = Set.of("u3", "u4", "u5");

        SegmentAnalyticsService.OverlapResult res = analytics.analyze(setA, setB);
        assertEquals(4, res.segmentASize());
        assertEquals(3, res.segmentBSize());
        assertEquals(2, res.intersectionSize()); // u3, u4
        assertEquals(5, res.unionSize());
        assertEquals(2.0 / 3.0, res.overlapRatio(), 0.001);
    }
}
