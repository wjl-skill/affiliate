# Affiliate Network Platform - Technical Design Document

## 1. System Architecture

### 1.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Load Balancer (Nginx)                     │
└──────────────────────────┬──────────────────────────────────────┘
                           │
    ┌──────────────────────┼──────────────────────┐
    │                      │                      │
┌───▼────┐          ┌──────▼──────┐      ┌───────▼────────┐
│ Click  │          │ Conversion  │      │  Dashboard/API │
│Tracker │          │  Postback   │      │   (Rest API)   │
│ (Hot)  │          │   (Hot)     │      │    (Warm)      │
└───┬────┘          └──────┬──────┘      └───────┬────────┘
    │                      │                      │
    │    ┌─────────────────┴──────────┬───────────┘
    │    │                            │
┌───▼────▼─────┐              ┌───────▼────────┐
│    Redis     │              │   PostgreSQL   │
│ (Click Cache)│              │   (Primary)    │
│ (Flood Ctrl) │              │                │
└──────────────┘              └────────┬───────┘
                                       │
                              ┌────────▼────────┐
                              │  Read Replicas  │
                              │  (Reporting)    │
                              └─────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                    Kafka Event Stream                         │
│  Topics: clicks, conversions, payments, fraud-alerts         │
└───┬────────────────┬─────────────────┬──────────────────┬────┘
    │                │                 │                  │
┌───▼──────┐  ┌──────▼─────┐  ┌───────▼───────┐  ┌──────▼──────┐
│Publisher │  │ Analytics  │  │  Anti-Fraud   │  │   Payment   │
│Postback  │  │ Aggregator │  │    Engine     │  │  Processor  │
│Dispatcher│  │  (Spark)   │  │   (ML Model)  │  │  (Batch)    │
└──────────┘  └────────────┘  └───────────────┘  └─────────────┘
```

### 1.2 Technology Stack

**Backend**:
- Java 21 with Spring Boot 3.4
- MyBatis-Plus for production persistence and ORM-style mapping
- Spring Security for authentication
- Maven for dependency management

**Databases**:
- PostgreSQL 14+ (primary transactional database)
- Redis 7+ (caching, rate limiting, session storage)
- Kafka 3.x (event streaming)

Production writes and reads use MyBatis-Plus `BaseMapper` implementations. The entity snippets below describe the domain shape; they are illustrative and do not imply Spring Data JPA repositories are enabled.

**Infrastructure**:
- Docker & Kubernetes for orchestration
- Nginx for load balancing
- Prometheus + Grafana for monitoring
- ELK Stack for logging

**External Services**:
- Payment processors: Stripe, PayPal, Wise
- Fraud detection: MaxMind GeoIP2, SEON
- Email: SendGrid
- CDN: CloudFront

### 1.3 Service Boundaries

The platform uses a **modular monolith** architecture with clear module boundaries, allowing future extraction into microservices:

| Module | Responsibility | Database Tables | Key Interfaces |
|--------|----------------|-----------------|----------------|
| **platform-affiliate** | Core affiliate business logic | affiliate_* tables | OfferService, ClickTrackerService, S2sPostbackService |
| **platform-auth** | Authentication & authorization | users, roles, api_keys | AuthenticationService, TenantContext |
| **platform-billing** | Financial transactions | billing_entry, billing_account | BillingService, WalletService |
| **platform-event** | Event publishing | event_outbox | EventPublisher, OutboxRelay |
| **platform-reporting** | Analytics & reports | report_daily, report_hourly | ReportService, SubIdAnalyticsService |
| **platform-infrastructure** | Data access adapters | N/A | MyBatis-Plus Mappers, Redis/Kafka clients |

**Dependency Rules**:
- Modules depend on `platform-common` (shared domain models)
- No circular dependencies between modules
- Infrastructure adapters implement interfaces defined in business modules
- API layer orchestrates calls across modules

## 2. Data Model

### 2.1 Core Domain Entities

#### Affiliate Partner
```java
@Entity
@Table(name = "affiliate_partner")
public class AffiliatePartner {
    @Id
    private String id;  // "aff_888"

    private String tenantId;
    private String name;

    @Enumerated(EnumType.STRING)
    private PartnerStatus status;  // ACTIVE, PENDING, SUSPENDED, BANNED

    @Enumerated(EnumType.STRING)
    private PartnerTier tier;  // STANDARD, SILVER, GOLD, VIP

    private String postbackUrlTemplate;

    @Enumerated(EnumType.STRING)
    private PaymentTerm paymentTerm;  // NET_7, NET_15, NET_30, WEEKLY

    private BigDecimal minPayoutThreshold;

    // Quality metrics
    private Integer qualityScore;  // 0-100
    private BigDecimal fraudRate;  // percentage

    private Instant createdAt;
    private Instant updatedAt;
}
```

#### Offer
```java
@Entity
@Table(name = "affiliate_offer")
public class Offer {
    @Id
    private String id;  // "off_101"

    private String tenantId;
    private String advertiserId;
    private String title;
    private String landingPageUrl;  // Template with macros

    @Enumerated(EnumType.STRING)
    private PayoutType payoutType;  // CPA, CPL, CPS, CPI, CPC

    private BigDecimal defaultPayout;
    private BigDecimal defaultRevenue;

    @Enumerated(EnumType.STRING)
    private OfferStatus status;  // ACTIVE, PAUSED, EXPIRED

    // Caps and limits
    private Integer dailyConversionCap;
    private BigDecimal dailyRevenueCap;
    private String fallbackOfferId;

    // Targeting
    @Column(columnDefinition = "text[]")
    private String[] allowedCountries;

    @Column(columnDefinition = "integer[]")
    private Integer[] allowedDevices;

    private Integer cookieDuration;  // days

    private Instant expiresAt;
    private Instant createdAt;
}
```

#### Click Session
```java
@Entity
@Table(name = "affiliate_click_session")
public class ClickSession {
    @Id
    private String clickId;  // Cryptographically secure random ID

    private String tenantId;
    private String offerId;
    private String affiliateId;

    // Sub-ID tracking
    private String sub1;
    private String sub2;
    private String sub3;
    private String sub4;
    private String sub5;

    // Visitor attributes
    private String ip;
    private String userAgent;
    private String country;

    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;  // MOBILE, TABLET, DESKTOP

    // Timestamps
    private Instant createdAt;
    private Instant expiresAt;  // createdAt + attribution window

    // Fraud indicators
    private Boolean proxyDetected;
    private Boolean botSuspected;
}
```

#### Conversion
```java
@Entity
@Table(name = "affiliate_conversion")
public class Conversion {
    @Id
    private String id;

    private String tenantId;
    private String clickId;
    private String txId;  // Advertiser transaction ID (idempotency)

    private String offerId;
    private String affiliateId;

    // Financial
    private BigDecimal payout;  // Affiliate commission
    private BigDecimal revenue;  // Advertiser payment
    private BigDecimal saleAmount;  // Order total (for CPS)

    // Attribution
    private Long ctitSeconds;  // Click-to-install-time

    @Enumerated(EnumType.STRING)
    private ConversionStatus status;  // PENDING, APPROVED, REJECTED, FRAUD_SUSPECTED

    private String rejectionReason;

    private Instant createdAt;
    private Instant approvedAt;
}
```

#### SmartLink
```java
@Entity
@Table(name = "affiliate_smart_link")
public class SmartLink {
    @Id
    private String id;  // "sl_12345"

    private String tenantId;
    private String name;
    private String category;

    @Column(columnDefinition = "text[]")
    private String[] targetOfferIds;

    @Enumerated(EnumType.STRING)
    private RoutingStrategy routingStrategy;  // HIGHEST_EPC, ROUND_ROBIN, WEIGHTED, GEO_OPTIMIZED

    private String fallbackOfferId;

    private Instant createdAt;
}
```

### 2.2 Database Indexes

Critical indexes for performance:

```sql
-- Click session lookups (hot path)
CREATE INDEX idx_click_session_lookup ON affiliate_click_session(click_id) WHERE expires_at > NOW();
CREATE INDEX idx_click_session_cleanup ON affiliate_click_session(expires_at) WHERE expires_at < NOW();

-- Conversion queries
CREATE UNIQUE INDEX uk_conversion_offer_tx ON affiliate_conversion(offer_id, tx_id);
CREATE INDEX idx_conversion_aff_status ON affiliate_conversion(tenant_id, affiliate_id, status, created_at DESC);
CREATE INDEX idx_conversion_approval ON affiliate_conversion(status, created_at) WHERE status = 'PENDING';

-- Reporting queries
CREATE INDEX idx_conversion_reporting ON affiliate_conversion(tenant_id, offer_id, affiliate_id, created_at, status);
CREATE INDEX idx_click_reporting ON affiliate_click_session(tenant_id, offer_id, affiliate_id, created_at);

-- Sub-ID analytics
CREATE INDEX idx_subid_stats_lookup ON affiliate_sub_id_stats(tenant_id, affiliate_id, sub1);

-- Offer queries
CREATE INDEX idx_offer_active ON affiliate_offer(tenant_id, status) WHERE status = 'ACTIVE';
CREATE INDEX idx_offer_advertiser ON affiliate_offer(tenant_id, advertiser_id);

-- Partner queries
CREATE INDEX idx_partner_tier ON affiliate_partner(tenant_id, tier, status);
```

### 2.3 Partitioning Strategy

For high-volume tables, use PostgreSQL native partitioning:

```sql
-- Partition click_session by month (retention: 90 days)
CREATE TABLE affiliate_click_session (
    ...
) PARTITION BY RANGE (created_at);

CREATE TABLE affiliate_click_session_2026_09 PARTITION OF affiliate_click_session
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');

-- Auto-create partitions via pg_partman extension
-- Auto-drop partitions older than 90 days

-- Partition conversions by year (retention: 7 years for tax compliance)
CREATE TABLE affiliate_conversion (
    ...
) PARTITION BY RANGE (created_at);

CREATE TABLE affiliate_conversion_2026 PARTITION OF affiliate_conversion
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
```

## 3. Hot Path Optimization: Click Tracking

### 3.1 Click Tracking Flow

The click endpoint must handle 10,000+ req/sec with P95 <50ms:

```java
@RestController
@RequestMapping("/affiliate")
public class AffiliateClickController {

    @GetMapping("/click")
    public ResponseEntity<Void> handleClick(
        @RequestParam String offerId,
        @RequestParam String affId,
        @RequestParam(required = false) String sub1,
        @RequestParam(required = false) String sub2,
        @RequestParam(required = false) String sub3,
        @RequestParam(required = false) String sub4,
        @RequestParam(required = false) String sub5,
        HttpServletRequest request
    ) {
        // 1. Extract visitor attributes (5ms)
        ClickAttributes attrs = extractAttributes(request);

        // 2. Validate offer and affiliate from cache (5ms)
        Offer offer = offerCache.get(offerId);
        if (offer == null || !offer.isActive()) {
            return ResponseEntity.notFound().build();
        }

        AffiliatePartner affiliate = affiliateCache.get(affId);
        if (affiliate == null || !affiliate.isActive()) {
            return ResponseEntity.notFound().build();
        }

        // 3. IP flood control via Redis (5ms)
        if (!rateLimiter.allowClick(attrs.ip())) {
            return ResponseEntity.status(429).build();  // Too Many Requests
        }

        // 4. Check targeting rules (5ms)
        if (!offer.matchesTargeting(attrs.country(), attrs.deviceType())) {
            return redirectToFallback(offer);
        }

        // 5. Check conversion cap (10ms)
        if (capChecker.isCapReached(offerId)) {
            return redirectToFallback(offer);
        }

        // 6. Generate click ID and store session (10ms)
        String clickId = generateSecureClickId();
        ClickSession session = new ClickSession(
            clickId, tenantId, offerId, affId,
            sub1, sub2, sub3, sub4, sub5,
            attrs.ip(), attrs.userAgent(), attrs.country(), attrs.deviceType()
        );

        // Store in Redis with 30-day TTL (async write-through to DB)
        clickCache.save(session);
        eventPublisher.publishClickEvent(session);  // Async

        // 7. Build redirect URL with macro replacement (5ms)
        String redirectUrl = buildRedirectUrl(offer.getLandingPageUrl(), clickId, session);

        // 8. Return 302 redirect (5ms)
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(redirectUrl))
            .build();
    }
}
```

### 3.2 Performance Optimizations

**Caching Strategy**:
```java
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redis) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(15))  // Refresh every 15 min
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            );

        return RedisCacheManager.builder(redis)
            .cacheDefaults(config)
            .withCacheConfiguration("offers", config.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("affiliates", config.entryTtl(Duration.ofMinutes(30)))
            .build();
    }
}
```

**Redis Rate Limiter**:
```java
@Service
public class RedisRateLimiter {

    private final RedisTemplate<String, String> redis;

    // Sliding window: max 60 clicks per IP per minute
    public boolean allowClick(String ip) {
        String key = "ratelimit:ip:" + ip;
        Long count = redis.opsForValue().increment(key);

        if (count == 1) {
            redis.expire(key, Duration.ofSeconds(60));
        }

        return count <= 60;
    }
}
```

**Async Event Publishing**:
```java
@Service
public class ClickEventPublisher {

    private final KafkaTemplate<String, ClickEvent> kafka;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public void publishClickEvent(ClickSession session) {
        executor.submit(() -> {
            ClickEvent event = new ClickEvent(session);
            kafka.send("clicks", session.getClickId(), event);
        });
    }
}
```

### 3.3 Cap Checking

Distributed cap checking using Redis Lua script for atomicity:

```lua
-- Redis Lua script: check_and_increment_cap.lua
local key = KEYS[1]
local cap = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])

local current = redis.call('GET', key)

if current == false then
    redis.call('SET', key, 1, 'EX', ttl)
    return 1
elseif tonumber(current) < cap then
    return redis.call('INCR', key)
else
    return -1  -- Cap reached
end
```

```java
@Service
public class CapChecker {

    private final RedisTemplate<String, String> redis;
    private final RedisScript<Long> checkCapScript;

    public boolean isCapReached(String offerId) {
        String key = "cap:daily:" + offerId + ":" + LocalDate.now();

        Offer offer = offerService.getOffer(offerId);
        if (offer.getDailyConversionCap() == null || offer.getDailyConversionCap() == 0) {
            return false;  // No cap
        }

        Long result = redis.execute(
            checkCapScript,
            Collections.singletonList(key),
            offer.getDailyConversionCap().toString(),
            "86400"  // TTL: 24 hours
        );

        return result != null && result == -1;
    }
}
```

## 4. Hot Path Optimization: Conversion Postback

### 4.1 S2S Postback Flow

Handle 1,000 req/sec with P99 <200ms:

```java
@RestController
@RequestMapping("/affiliate")
public class S2sPostbackController {

    @PostMapping("/postback")
    public ResponseEntity<ConversionResponse> recordConversion(
        @RequestParam String clickId,
        @RequestParam String txid,
        @RequestParam(required = false) BigDecimal saleAmount,
        HttpServletRequest request
    ) {
        // 1. Authenticate request (10ms)
        if (!authenticatePostback(request)) {
            return ResponseEntity.status(401).build();
        }

        // 2. Retrieve click session (15ms - Redis first, fallback to DB)
        ClickSession click = clickCache.get(clickId);
        if (click == null) {
            click = clickRepository.findById(clickId)
                .orElseThrow(() -> new NotFoundException("Invalid click_id"));
        }

        // 3. Check attribution window (5ms)
        if (click.isExpired()) {
            throw new AttributionExpiredException("Click expired");
        }

        // 4. Check duplicate (10ms - Bloom filter + Redis)
        String dupeKey = "conversion:" + click.getOfferId() + ":" + txid;
        if (!redis.setIfAbsent(dupeKey, "1", Duration.ofDays(90))) {
            throw new DuplicateConversionException("Transaction already recorded");
        }

        // 5. Calculate CTIT (5ms)
        long ctitSeconds = Duration.between(click.getCreatedAt(), Instant.now()).getSeconds();

        // 6. Apply fraud detection (20ms)
        FraudCheckResult fraudCheck = antiFraudEngine.evaluate(click, ctitSeconds, saleAmount);
        ConversionStatus status = fraudCheck.isSuspicious() ?
            ConversionStatus.FRAUD_SUSPECTED : ConversionStatus.PENDING;

        // 7. Determine payout (10ms - check tier overrides)
        PayoutCalculation payout = payoutCalculator.calculate(
            click.getOfferId(),
            click.getAffiliateId(),
            saleAmount
        );

        // 8. Store conversion (30ms - DB write)
        Conversion conversion = new Conversion();
        conversion.setId(generateConversionId());
        conversion.setTenantId(click.getTenantId());
        conversion.setClickId(clickId);
        conversion.setTxId(txid);
        conversion.setOfferId(click.getOfferId());
        conversion.setAffiliateId(click.getAffiliateId());
        conversion.setPayout(payout.affiliateCommission());
        conversion.setRevenue(payout.advertiserCost());
        conversion.setSaleAmount(saleAmount);
        conversion.setCtitSeconds(ctitSeconds);
        conversion.setStatus(status);
        conversion.setRejectionReason(fraudCheck.getReason());

        conversionRepository.save(conversion);

        // 9. Publish events (async, 5ms)
        eventPublisher.publishConversionEvent(conversion, click);

        // 10. Fire publisher postback (async)
        postbackDispatcher.dispatch(conversion, click);

        // 11. Update Sub-ID stats (async)
        analyticsService.updateSubIdStats(click, conversion);

        return ResponseEntity.ok(new ConversionResponse(
            conversion.getId(),
            conversion.getStatus().name(),
            payout.affiliateCommission()
        ));
    }
}
```

### 4.2 Payout Calculation with Tier Override

```java
@Service
public class PayoutCalculator {

    public PayoutCalculation calculate(String offerId, String affiliateId, BigDecimal saleAmount) {
        // 1. Get base offer payout
        Offer offer = offerService.getOffer(offerId);
        BigDecimal payout = offer.getDefaultPayout();
        BigDecimal revenue = offer.getDefaultRevenue();

        // 2. Check affiliate-specific override
        Optional<OfferTierPayout> affiliateOverride = tierPayoutRepository
            .findByOfferIdAndAffiliateId(offerId, affiliateId);

        if (affiliateOverride.isPresent()) {
            payout = affiliateOverride.get().getCustomPayout();
            revenue = affiliateOverride.get().getCustomRevenue();
        } else {
            // 3. Check tier-based override
            AffiliatePartner affiliate = affiliateService.getPartner(affiliateId);
            Optional<OfferTierPayout> tierOverride = tierPayoutRepository
                .findByOfferIdAndTargetTier(offerId, affiliate.getTier());

            if (tierOverride.isPresent()) {
                payout = tierOverride.get().getCustomPayout();
                revenue = tierOverride.get().getCustomRevenue();
            }
        }

        // 4. For CPS (percentage-based), calculate from sale amount
        if (offer.getPayoutType() == PayoutType.CPS && saleAmount != null) {
            payout = saleAmount.multiply(payout).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            revenue = saleAmount.multiply(revenue).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }

        return new PayoutCalculation(payout, revenue);
    }
}
```

### 4.3 Anti-Fraud Detection

```java
@Service
public class AffiliateAntiFraudEngine {

    public FraudCheckResult evaluate(ClickSession click, long ctitSeconds, BigDecimal saleAmount) {
        List<String> flags = new ArrayList<>();
        int riskScore = 0;

        // Rule 1: CTIT too fast (click injection)
        if (ctitSeconds < 3) {
            flags.add("CTIT_UNDER_3_SECONDS");
            riskScore += 80;  // Very suspicious
        }

        // Rule 2: CTIT suspiciously fast for human behavior
        if (ctitSeconds >= 3 && ctitSeconds < 10) {
            flags.add("CTIT_UNDER_10_SECONDS");
            riskScore += 40;
        }

        // Rule 3: Proxy/VPN detected at click time
        if (Boolean.TRUE.equals(click.getProxyDetected())) {
            flags.add("PROXY_DETECTED");
            riskScore += 30;
        }

        // Rule 4: Bot suspected
        if (Boolean.TRUE.equals(click.getBotSuspected())) {
            flags.add("BOT_USER_AGENT");
            riskScore += 50;
        }

        // Rule 5: Conversion rate anomaly (check affiliate history)
        AffiliateStats stats = statsService.getStats(click.getAffiliateId());
        double normalCR = stats.getConversionRate();
        double recentCR = stats.getRecentConversionRate(Duration.ofHours(24));

        if (recentCR > normalCR * 3) {
            flags.add("CONVERSION_RATE_SPIKE");
            riskScore += 60;
        }

        // Rule 6: Unusually high sale amount
        Offer offer = offerService.getOffer(click.getOfferId());
        if (saleAmount != null) {
            BigDecimal avgSale = offer.getAverageSaleAmount();
            if (saleAmount.compareTo(avgSale.multiply(BigDecimal.valueOf(5))) > 0) {
                flags.add("UNUSUALLY_HIGH_SALE_AMOUNT");
                riskScore += 20;
            }
        }

        // Determine verdict
        if (riskScore >= 80) {
            return new FraudCheckResult(true, "HIGH_RISK: " + String.join(", ", flags), riskScore);
        } else if (riskScore >= 40) {
            return new FraudCheckResult(false, "MEDIUM_RISK: " + String.join(", ", flags), riskScore);
        } else {
            return new FraudCheckResult(false, null, riskScore);
        }
    }
}
```

## 5. SmartLink/TDS Implementation

### 5.1 TDS Router

```java
@Service
public class TdsRouter {

    public String selectOffer(SmartLink smartLink, ClickAttributes attrs) {
        // 1. Get candidate offers
        List<String> candidateIds = Arrays.asList(smartLink.getTargetOfferIds());
        List<Offer> candidates = offerService.getOffers(candidateIds).stream()
            .filter(Offer::isActive)
            .filter(o -> o.matchesTargeting(attrs.country(), attrs.deviceType()))
            .filter(o -> !capChecker.isCapReached(o.getId()))
            .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return smartLink.getFallbackOfferId();
        }

        // 2. Apply routing strategy
        return switch (smartLink.getRoutingStrategy()) {
            case HIGHEST_EPC -> selectByHighestEpc(candidates, attrs);
            case ROUND_ROBIN -> selectRoundRobin(candidates);
            case WEIGHTED -> selectWeighted(candidates);
            case GEO_OPTIMIZED -> selectByGeo(candidates, attrs);
        };
    }

    private String selectByHighestEpc(List<Offer> candidates, ClickAttributes attrs) {
        // Query historical EPC from analytics
        Map<String, BigDecimal> epcMap = analyticsService.getEpcByOfferAndSegment(
            candidates.stream().map(Offer::getId).collect(Collectors.toList()),
            attrs.country(),
            attrs.deviceType(),
            Duration.ofDays(7)  // Last 7 days
        );

        // Select offer with highest EPC
        return candidates.stream()
            .max(Comparator.comparing(o -> epcMap.getOrDefault(o.getId(), BigDecimal.ZERO)))
            .map(Offer::getId)
            .orElse(candidates.get(0).getId());
    }

    private String selectRoundRobin(List<Offer> candidates) {
        // Use Redis counter for distributed round-robin
        String key = "rr:" + String.join(",", candidates.stream().map(Offer::getId).toList());
        Long index = redis.opsForValue().increment(key);
        return candidates.get((int) (index % candidates.size())).getId();
    }

    private String selectWeighted(List<Offer> candidates) {
        // Get weights from offer configuration
        List<WeightedOffer> weighted = candidates.stream()
            .map(o -> new WeightedOffer(o.getId(), o.getWeight()))
            .collect(Collectors.toList());

        return weightedRandomSelector.select(weighted);
    }

    private String selectByGeo(List<Offer> candidates, ClickAttributes attrs) {
        // Tier 1 GEOs: US, CA, UK, AU
        boolean isTier1 = Set.of("US", "CA", "UK", "AU").contains(attrs.country());

        return candidates.stream()
            .filter(o -> isTier1 ? o.isTier1Geo() : !o.isTier1Geo())
            .findFirst()
            .map(Offer::getId)
            .orElse(candidates.get(0).getId());
    }
}
```

### 5.2 EPC Calculation and Caching

```java
@Service
public class SubIdAnalyticsService {

    // Real-time incremental update on each conversion
    @Transactional
    public void updateSubIdStats(ClickSession click, Conversion conversion) {
        String key = SubIdStatsKey.of(
            click.getTenantId(),
            click.getAffiliateId(),
            click.getSub1()
        );

        // Upsert stats record
        SubIdStats stats = statsRepository.findById(key)
            .orElse(new SubIdStats(click.getTenantId(), click.getAffiliateId(), click.getSub1()));

        stats.incrementClicks(1);  // Actually increment on click event
        if (conversion.getStatus() == ConversionStatus.APPROVED) {
            stats.incrementConversions(1);
            stats.addPayout(conversion.getPayout());
            stats.addRevenue(conversion.getRevenue());
        }

        // Recalculate derived metrics
        stats.calculateEpc();  // EPC = total_payout / clicks
        stats.calculateCr();   // CR% = conversions / clicks * 100

        statsRepository.save(stats);
    }

    // Get EPC for routing decisions (cached)
    @Cacheable(value = "epc", key = "#offerId + ':' + #country + ':' + #device")
    public BigDecimal getEpc(String offerId, String country, DeviceType device, Duration window) {
        Instant since = Instant.now().minus(window);

        // Aggregate from conversions and clicks
        BigDecimal totalPayout = conversionRepository
            .sumPayoutByOfferAndSegmentSince(offerId, country, device, since);

        Long totalClicks = clickRepository
            .countByOfferAndSegmentSince(offerId, country, device, since);

        if (totalClicks == 0) {
            return BigDecimal.ZERO;
        }

        return totalPayout.divide(BigDecimal.valueOf(totalClicks), 4, RoundingMode.HALF_UP);
    }
}
```

## 6. Publisher Postback Dispatcher

### 6.1 Async Postback Delivery

```java
@Service
public class PublisherPostbackDispatcher {

    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, PostbackTask> kafka;

    public void dispatch(Conversion conversion, ClickSession click) {
        // Get affiliate postback URL template
        AffiliatePartner affiliate = affiliateService.getPartner(conversion.getAffiliateId());

        if (affiliate.getPostbackUrlTemplate() == null) {
            return;  // No postback configured
        }

        // Create postback task
        PostbackTask task = new PostbackTask();
        task.setAffiliateId(affiliate.getId());
        task.setUrlTemplate(affiliate.getPostbackUrlTemplate());
        task.setConversion(conversion);
        task.setClickSession(click);
        task.setAttempt(0);

        // Send to Kafka for async processing
        kafka.send("postback-tasks", task);
    }
}

@Service
@KafkaListener(topics = "postback-tasks", groupId = "postback-processor")
public class PostbackProcessor {

    private final RestTemplate restTemplate;

    @KafkaHandler
    public void processPostback(PostbackTask task) {
        try {
            // Replace macros in URL template
            String url = replaceMacros(task.getUrlTemplate(), task.getConversion(), task.getClickSession());

            // Fire HTTP request with timeout
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Postback delivered: affiliate={}, conversion={}",
                    task.getAffiliateId(), task.getConversion().getId());
            } else {
                throw new PostbackFailedException("HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            handleFailure(task, e);
        }
    }

    private void handleFailure(PostbackTask task, Exception e) {
        task.setAttempt(task.getAttempt() + 1);

        if (task.getAttempt() < 3) {
            // Retry with exponential backoff
            long delayMs = (long) Math.pow(10, task.getAttempt()) * 1000;  // 10s, 100s, 1000s

            try {
                Thread.sleep(delayMs);
                kafka.send("postback-tasks", task);  // Re-queue
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        } else {
            // Max retries exceeded, log for manual review
            log.error("Postback failed after 3 attempts: affiliate={}, conversion={}, error={}",
                task.getAffiliateId(), task.getConversion().getId(), e.getMessage());

            // Store in dead-letter table
            failedPostbackRepository.save(new FailedPostback(task, e.getMessage()));
        }
    }

    private String replaceMacros(String template, Conversion conv, ClickSession click) {
        return template
            .replace("{click_id}", click.getClickId())
            .replace("{payout}", conv.getPayout().toString())
            .replace("{revenue}", conv.getRevenue().toString())
            .replace("{txid}", conv.getTxId())
            .replace("{status}", conv.getStatus().name().toLowerCase())
            .replace("{offer_id}", conv.getOfferId())
            .replace("{affiliate_id}", conv.getAffiliateId())
            .replace("{sale_amount}", conv.getSaleAmount() != null ? conv.getSaleAmount().toString() : "0")
            .replace("{sub1}", click.getSub1() != null ? click.getSub1() : "")
            .replace("{sub2}", click.getSub2() != null ? click.getSub2() : "")
            .replace("{sub3}", click.getSub3() != null ? click.getSub3() : "")
            .replace("{sub4}", click.getSub4() != null ? click.getSub4() : "")
            .replace("{sub5}", click.getSub5() != null ? click.getSub5() : "");
    }
}
```

## 7. Payment Settlement System

### 7.1 Settlement Job

```java
@Service
public class AffiliateSettlementService {

    @Scheduled(cron = "0 0 2 * * *")  // Run daily at 2 AM
    public void processSettlements() {
        LocalDate today = LocalDate.now();

        // Process each payment term
        processNetTerms(PaymentTerm.NET_7, today.minusDays(7));
        processNetTerms(PaymentTerm.NET_15, today.minusDays(15));
        processNetTerms(PaymentTerm.NET_30, today.minusDays(30));
        processWeeklyPayments(today);
    }

    private void processNetTerms(PaymentTerm term, LocalDate cutoffDate) {
        // Find affiliates with this payment term
        List<AffiliatePartner> affiliates = affiliateRepository.findByPaymentTerm(term);

        for (AffiliatePartner affiliate : affiliates) {
            // Calculate balance from approved conversions
            BigDecimal balance = conversionRepository.sumPayoutByAffiliateAndStatus(
                affiliate.getId(),
                ConversionStatus.APPROVED,
                cutoffDate  // Conversions before cutoff date
            );

            // Check minimum threshold
            if (balance.compareTo(affiliate.getMinPayoutThreshold()) < 0) {
                continue;  // Below threshold, skip
            }

            // Create invoice
            AffiliateInvoice invoice = new AffiliateInvoice();
            invoice.setId(generateInvoiceId());
            invoice.setTenantId(affiliate.getTenantId());
            invoice.setAffiliateId(affiliate.getId());
            invoice.setAmount(balance);
            invoice.setPaymentTerm(term);
            invoice.setStatus(InvoiceStatus.GENERATED);
            invoice.setCreatedAt(Instant.now());

            // Count conversions in this invoice
            int convCount = conversionRepository.countByAffiliateAndStatusBeforeCutoff(
                affiliate.getId(),
                ConversionStatus.APPROVED,
                cutoffDate
            );
            invoice.setConversionCount(convCount);

            invoiceRepository.save(invoice);

            // Mark conversions as paid (status remains APPROVED, but add invoice_id)
            conversionRepository.markAsPaid(affiliate.getId(), invoice.getId(), cutoffDate);

            // Send notification
            notificationService.sendInvoiceGenerated(affiliate, invoice);

            log.info("Generated invoice: {} for affiliate: {} amount: {}",
                invoice.getId(), affiliate.getId(), balance);
        }
    }

    // Execute actual payments
    @Scheduled(cron = "0 0 10 * * *")  // Run daily at 10 AM
    public void executePayments() {
        List<AffiliateInvoice> pending = invoiceRepository.findByStatus(InvoiceStatus.GENERATED);

        for (AffiliateInvoice invoice : pending) {
            try {
                AffiliatePartner affiliate = affiliateService.getPartner(invoice.getAffiliateId());

                // Execute payment via configured method
                PaymentResult result = paymentGateway.pay(
                    affiliate.getPaymentMethod(),
                    affiliate.getPaymentDetails(),
                    invoice.getAmount()
                );

                if (result.isSuccess()) {
                    invoice.setStatus(InvoiceStatus.PAID);
                    invoice.setPaymentTransactionId(result.getTransactionId());
                    invoiceRepository.save(invoice);

                    notificationService.sendPaymentConfirmation(affiliate, invoice);

                    log.info("Payment sent: invoice={} amount={} txId={}",
                        invoice.getId(), invoice.getAmount(), result.getTransactionId());
                } else {
                    invoice.setStatus(InvoiceStatus.FAILED);
                    invoice.setFailureReason(result.getError());
                    invoiceRepository.save(invoice);

                    notificationService.sendPaymentFailed(affiliate, invoice);
                }

            } catch (Exception e) {
                log.error("Payment execution failed: invoice={}, error={}",
                    invoice.getId(), e.getMessage(), e);
            }
        }
    }
}
```

## 8. Monitoring and Observability

### 8.1 Key Metrics

```java
@Component
public class AffiliateMetrics {

    private final MeterRegistry registry;

    // Counters
    private final Counter clicksTotal;
    private final Counter conversionsTotal;
    private final Counter fraudDetected;
    private final Counter postbacksFailed;

    // Gauges
    private final AtomicInteger activeAffiliates;
    private final AtomicInteger activeOffers;

    // Timers
    private final Timer clickLatency;
    private final Timer conversionLatency;
    private final Timer postbackLatency;

    public AffiliateMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.clicksTotal = Counter.builder("affiliate.clicks.total")
            .description("Total clicks processed")
            .register(registry);

        this.conversionsTotal = Counter.builder("affiliate.conversions.total")
            .tag("status", "all")
            .description("Total conversions recorded")
            .register(registry);

        this.fraudDetected = Counter.builder("affiliate.fraud.detected")
            .description("Fraudulent conversions detected")
            .register(registry);

        this.clickLatency = Timer.builder("affiliate.click.latency")
            .description("Click endpoint latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.conversionLatency = Timer.builder("affiliate.conversion.latency")
            .description("Conversion endpoint latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    public void recordClick() {
        clicksTotal.increment();
    }

    public void recordConversion(ConversionStatus status) {
        conversionsTotal.increment();

        if (status == ConversionStatus.FRAUD_SUSPECTED) {
            fraudDetected.increment();
        }
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }
}
```

### 8.2 Health Checks

```java
@Component
public class AffiliateHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final RedisConnectionFactory redis;
    private final KafkaTemplate<String, Object> kafka;

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();

        // Check database
        try {
            dataSource.getConnection().close();
            builder.withDetail("database", "UP");
        } catch (Exception e) {
            return builder.down().withDetail("database", e.getMessage()).build();
        }

        // Check Redis
        try {
            redis.getConnection().ping();
            builder.withDetail("redis", "UP");
        } catch (Exception e) {
            return builder.down().withDetail("redis", e.getMessage()).build();
        }

        // Check Kafka
        try {
            kafka.send("health-check", "ping").get(5, TimeUnit.SECONDS);
            builder.withDetail("kafka", "UP");
        } catch (Exception e) {
            builder.withDetail("kafka", "DEGRADED");
        }

        return builder.up().build();
    }
}
```

### 8.3 Alerting Rules

```yaml
# Prometheus alerting rules
groups:
  - name: affiliate_platform
    interval: 30s
    rules:
      - alert: HighClickLatency
        expr: histogram_quantile(0.99, affiliate_click_latency_seconds) > 0.1
        for: 5m
        annotations:
          summary: "Click endpoint P99 latency above 100ms"

      - alert: HighFraudRate
        expr: rate(affiliate_fraud_detected[5m]) / rate(affiliate_conversions_total[5m]) > 0.05
        for: 10m
        annotations:
          summary: "Fraud rate exceeds 5%"

      - alert: PostbackFailureRate
        expr: rate(affiliate_postbacks_failed[5m]) > 10
        for: 5m
        annotations:
          summary: "High postback failure rate"

      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 2m
        annotations:
          summary: "Database connection pool near capacity"
```

## 9. Security Considerations

### 9.1 API Authentication

```java
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()  // Stateless API
            .authorizeHttpRequests(auth -> auth
                // Public endpoints (no auth required)
                .requestMatchers("/affiliate/click").permitAll()
                .requestMatchers("/affiliate/postback").permitAll()

                // Advertiser API (API key required)
                .requestMatchers("/api/v1/offers/**").hasRole("ADVERTISER")
                .requestMatchers("/api/v1/conversions/**").hasRole("ADVERTISER")

                // Affiliate API (API key required)
                .requestMatchers("/api/v1/reports/**").hasRole("AFFILIATE")
                .requestMatchers("/api/v1/links/**").hasRole("AFFILIATE")

                // Admin API (JWT required)
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthFilter() {
        return new ApiKeyAuthenticationFilter(apiKeyService());
    }
}
```

### 9.2 Rate Limiting

```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, String> redis;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null) {
            return true;  // Public endpoint, no rate limit
        }

        String key = "ratelimit:api:" + apiKey;
        Long count = redis.opsForValue().increment(key);

        if (count == 1) {
            redis.expire(key, Duration.ofHours(1));
        }

        if (count > 1000) {  // 1000 requests per hour
            response.setStatus(429);
            response.setHeader("X-RateLimit-Limit", "1000");
            response.setHeader("X-RateLimit-Remaining", "0");
            return false;
        }

        response.setHeader("X-RateLimit-Limit", "1000");
        response.setHeader("X-RateLimit-Remaining", String.valueOf(1000 - count));

        return true;
    }
}
```

## 10. Deployment Architecture

### 10.1 Kubernetes Deployment

```yaml
# click-tracker-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: click-tracker
spec:
  replicas: 5
  selector:
    matchLabels:
      app: click-tracker
  template:
    metadata:
      labels:
        app: click-tracker
    spec:
      containers:
      - name: click-tracker
        image: affiliate-platform:latest
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod,click-tracker"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: click-tracker-service
spec:
  selector:
    app: click-tracker
  ports:
  - port: 80
    targetPort: 8080
  type: LoadBalancer
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: click-tracker-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: click-tracker
  minReplicas: 5
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

### 10.2 Infrastructure Requirements

**Minimum Production Setup**:

- **Application Servers**: 5x instances (2 vCPU, 4GB RAM each)
- **PostgreSQL**: Primary + 2 read replicas (8 vCPU, 32GB RAM)
- **Redis**: 3-node cluster (4GB RAM each)
- **Kafka**: 3-node cluster (4 vCPU, 8GB RAM each)
- **Load Balancer**: AWS ALB or GCP Load Balancer
- **CDN**: CloudFront for static assets

**Estimated Costs** (AWS):
- Compute (EC2): $800/month
- Database (RDS PostgreSQL): $600/month
- Redis (ElastiCache): $300/month
- Kafka (MSK): $500/month
- Load Balancer: $50/month
- CloudFront: $100/month
- **Total**: ~$2,350/month

---

**Document Version**: 1.0
**Last Updated**: 2026-09-08
**Owner**: Engineering Team
**Status**: Approved for Implementation
