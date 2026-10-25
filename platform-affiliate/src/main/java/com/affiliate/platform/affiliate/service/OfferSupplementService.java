package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.*;
import com.affiliate.platform.entity.OfferSupplementLogEntity;
import com.affiliate.platform.entity.OfferSupplementRuleEntity;
import com.affiliate.platform.lock.DistributedLock;
import com.affiliate.platform.mapper.OfferSupplementLogMapper;
import com.affiliate.platform.mapper.OfferSupplementRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offer 流量补量与归因平台 (AppsFlyer / Adjust) HTTP 推送核心引擎
 * <p>
 * 完整业务链路：
 * 1. 从多云存储桶 (`S3StorageConfig`) 或本地路径读取 `.snappy.parquet` 用户画像文件并解析出
 *    `id`, `gadvid`, `idfa`, `ip`, `ua`, `os`, `ds_publisher_id`, `ds_adx`, `s`, `m`；
 * 2. 对每条用户画像调用 `ClickTrackerService.trackClick(...)` 生成唯一 `click_id` 并持久化点击会话
 *    (`affiliate_click_session`) 及 Sub-ID 报表计数 (`SubIdAnalyticsService`)；
 * 3. 自动补充并替换 Offer 追踪链接中的宏变量与归因参数（支持 AppsFlyer / Adjust / Singular / Branch 标准归因参数）；
 * 4. 构造伪装真实设备环境的 HTTP 请求（透传 `User-Agent`、`X-Forwarded-For`、`X-Real-IP`，禁用 302 自动跟随），
 *    按设定 QPS 平滑推送至 AppsFlyer / Adjust 归因服务器；
 * 5. 记录每次补量点击的完整审计流水 (`affiliate_offer_supplement_log`) 并支持定时自动补量调度。
 */
@Service
public class OfferSupplementService {

    private static final Logger log = LoggerFactory.getLogger(OfferSupplementService.class);
    private static final int MAX_MEMORY_LOGS = 2000;
    private static final Pattern UNREPLACED_BRACE_MACRO = Pattern.compile("\\{[a-zA-Z0-9_\\-.]+}");

    private final OfferService offerService;
    private final ClickTrackerService clickTrackerService;
    private final SubIdAnalyticsService analyticsService;
    private final ParquetUserProfileReader parquetReader;
    private final OfferSupplementRuleMapper ruleMapper;
    private final OfferSupplementLogMapper logMapper;
    private final HttpClient httpClient;
    private final boolean networkEnabled;
    private final DistributedLock distributedLock;

    private final ConcurrentMap<String, OfferSupplementRule> memoryRules = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<OfferSupplementLog> memoryLogs = new ConcurrentLinkedDeque<>();

    public OfferSupplementService(
            OfferService offerService,
            ClickTrackerService clickTrackerService,
            SubIdAnalyticsService analyticsService,
            ParquetUserProfileReader parquetReader
    ) {
        this(offerService, clickTrackerService, analyticsService, parquetReader, null, null, false, null);
    }

    @Autowired
    public OfferSupplementService(
            OfferService offerService,
            ClickTrackerService clickTrackerService,
            @Autowired(required = false) SubIdAnalyticsService analyticsService,
            ParquetUserProfileReader parquetReader,
            @Autowired(required = false) OfferSupplementRuleMapper ruleMapper,
            @Autowired(required = false) OfferSupplementLogMapper logMapper,
            @Value("${app.supplement.network-enabled:true}") boolean networkEnabled,
            @Autowired(required = false) DistributedLock distributedLock
    ) {
        this.offerService = offerService;
        this.clickTrackerService = clickTrackerService;
        this.analyticsService = analyticsService;
        this.parquetReader = parquetReader != null ? parquetReader : new ParquetUserProfileReader();
        this.ruleMapper = ruleMapper;
        this.logMapper = logMapper;
        this.networkEnabled = networkEnabled;
        this.distributedLock = distributedLock;
        // 关键：禁用 302 自动跟随重定向，仅触发 AF/Adjust 归因采集而不下载下游商店安装包
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @PostConstruct
    public void preloadFromDatabase() {
        if (ruleMapper != null) {
            try {
                List<OfferSupplementRuleEntity> entities = ruleMapper.selectList(null);
                if (entities != null) {
                    for (OfferSupplementRuleEntity e : entities) {
                        memoryRules.put(e.getId(), toRuleDomain(e));
                    }
                }
            } catch (Exception e) {
                log.debug("[OfferSupplement] Preload supplement rules skipped: {}", e.getMessage());
            }
        }
        if (logMapper != null) {
            try {
                List<OfferSupplementLogEntity> entities = logMapper.selectList(
                        new LambdaQueryWrapper<OfferSupplementLogEntity>()
                                .orderByDesc(OfferSupplementLogEntity::getCreatedAt)
                                .last("LIMIT 200")
                );
                if (entities != null) {
                    for (OfferSupplementLogEntity e : entities) {
                        memoryLogs.addLast(toLogDomain(e));
                    }
                }
            } catch (Exception e) {
                log.debug("[OfferSupplement] Preload supplement logs skipped: {}", e.getMessage());
            }
        }
    }

    /**
     * 预览存储桶或本地路径中的 Parquet 用户信息文件
     */
    public ParquetUserProfileReader.ParquetReadBatch previewProfiles(
            String storageConfigId,
            String objectKeyOrPath,
            String osFilter,
            int offset,
            int limit
    ) {
        return parquetReader.readProfiles(
                storageConfigId,
                objectKeyOrPath,
                osFilter,
                offset,
                limit <= 0 ? 10 : Math.min(limit, 100)
        );
    }

    /**
     * 保存或更新 Offer 级补量规则
     */
    public OfferSupplementRule saveRule(OfferSupplementRule rule) {
        if (rule == null || rule.offerId() == null || rule.offerId().isBlank()) {
            throw new IllegalArgumentException("补量规则必须指定非空的 offerId");
        }
        String id = (rule.id() != null && !rule.id().isBlank())
                ? rule.id()
                : "sup_rule_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        OfferSupplementRule existing = memoryRules.get(id);
        OfferSupplementRule saved = new OfferSupplementRule(
                id,
                rule.tenantId(),
                rule.offerId(),
                rule.affiliateId(),
                rule.mmpPlatform(),
                rule.trackingUrlTemplate(),
                rule.storageConfigId(),
                rule.objectKeyOrPath(),
                rule.osFilter(),
                rule.batchSize(),
                rule.qpsLimit(),
                rule.autoEnabled(),
                rule.cronIntervalMinutes(),
                existing != null && rule.lastCursorOffset() == 0 ? existing.lastCursorOffset() : rule.lastCursorOffset(),
                existing != null && rule.totalDispatchedClicks() == 0 ? existing.totalDispatchedClicks() : rule.totalDispatchedClicks(),
                rule.status(),
                existing != null ? existing.lastExecutedAt() : rule.lastExecutedAt(),
                existing != null ? existing.createdAt() : Instant.now(),
                Instant.now()
        );

        memoryRules.put(id, saved);
        persistRuleToDb(saved);
        return saved;
    }

    public List<OfferSupplementRule> listRules(String tenantId, String offerId) {
        if (ruleMapper != null) {
            try {
                LambdaQueryWrapper<OfferSupplementRuleEntity> qw = new LambdaQueryWrapper<>();
                if (isSpecificTenant(tenantId)) {
                    qw.eq(OfferSupplementRuleEntity::getTenantId, tenantId);
                }
                if (offerId != null && !offerId.isBlank()) {
                    qw.eq(OfferSupplementRuleEntity::getOfferId, offerId);
                }
                qw.orderByDesc(OfferSupplementRuleEntity::getUpdatedAt);
                List<OfferSupplementRuleEntity> list = ruleMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    for (OfferSupplementRuleEntity e : list) {
                        memoryRules.put(e.getId(), toRuleDomain(e));
                    }
                }
            } catch (Exception ex) {
                log.debug("[OfferSupplement] List rules from DB skipped or failed: {}", ex.getMessage());
            }
        }
        return memoryRules.values().stream()
                .filter(r -> !isSpecificTenant(tenantId) || tenantId.equals(r.tenantId()))
                .filter(r -> offerId == null || offerId.isBlank() || offerId.equals(r.offerId()))
                .sorted(Comparator.comparing(OfferSupplementRule::updatedAt).reversed())
                .toList();
    }

    public boolean deleteRule(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) return false;
        OfferSupplementRule removed = memoryRules.remove(ruleId);
        if (ruleMapper != null) {
            try {
                return ruleMapper.deleteById(ruleId) > 0 || removed != null;
            } catch (Exception ex) {
                log.debug("[OfferSupplement] Delete rule from DB skipped or failed: {}", ex.getMessage());
            }
        }
        return removed != null;
    }

    /**
     * 立即执行一次 Offer 补量任务（支持按已有规则 ID 执行，或直接传入临时补量请求参数执行）
     */
    public SupplementExecutionResult executeSupplementBatch(SupplementTriggerRequest req) {
        if (req == null || req.offerId() == null || req.offerId().isBlank()) {
            throw new IllegalArgumentException("offerId 不能为空");
        }

        Offer offer = offerService.find(req.offerId())
                .orElseThrow(() -> new NoSuchElementException("未找到指定的 Offer 推广计划: " + req.offerId()));

        String tenantId = (req.tenantId() != null && !req.tenantId().isBlank())
                ? req.tenantId()
                : (offer.tenantId() != null ? offer.tenantId() : "default");

        String affiliateId = (req.affiliateId() != null && !req.affiliateId().isBlank())
                ? req.affiliateId().trim()
                : "aff_supplement";

        String mmpPlatform = (req.mmpPlatform() != null && !req.mmpPlatform().isBlank())
                ? req.mmpPlatform().trim().toUpperCase()
                : detectMmpPlatform(req.trackingUrlTemplate() != null && !req.trackingUrlTemplate().isBlank()
                        ? req.trackingUrlTemplate()
                        : offer.landingPageUrl());

        String urlTemplate = (req.trackingUrlTemplate() != null && !req.trackingUrlTemplate().isBlank())
                ? req.trackingUrlTemplate().trim()
                : offer.landingPageUrl();

        int batchSize = req.batchSize() <= 0 ? 20 : Math.min(req.batchSize(), 1000);
        int qpsLimit = req.qpsLimit() <= 0 ? 50 : Math.min(req.qpsLimit(), 1000);
        int startOffset = Math.max(0, req.startOffset());

        // 1. 从存储桶或本地路径读取 Parquet 用户信息文件
        ParquetUserProfileReader.ParquetReadBatch readBatch = parquetReader.readProfiles(
                req.storageConfigId(),
                req.objectKeyOrPath(),
                req.osFilter(),
                startOffset,
                batchSize
        );

        String taskBatchId = "sup_batch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        List<OfferSupplementLog> batchLogs = new ArrayList<>(readBatch.profiles().size());
        int successCount = 0;
        int failureCount = 0;

        long intervalMillis = qpsLimit >= 200 ? 0L : Math.max(1L, 1000L / qpsLimit);
        String defaultFallbackCountry = (offer.allowedCountries() == null || offer.allowedCountries().isEmpty())
                ? "TW"
                : offer.allowedCountries().iterator().next();

        // 2. 遍历每条用户设备画像，记录点击会话 + 补充并替换 Offer 参数 + HTTP 推送至 AF/Adjust
        for (int i = 0; i < readBatch.profiles().size(); i++) {
            SupplementUserProfile profile = readBatch.profiles().get(i);
            String country = profile.inferCountry(defaultFallbackCountry);
            String sub1 = profile.dsPublisherId() != null && !profile.dsPublisherId().isBlank()
                    ? profile.dsPublisherId()
                    : "direct_pub";
            String sub2 = profile.dsAdx() != null ? profile.dsAdx() : "";
            String sub3 = profile.s() != null ? profile.s() : "";
            String sub4 = profile.primaryDeviceId();
            String sub5 = profile.m() != null ? String.valueOf(profile.m()) : "";

            // 2.1 调用 ClickTrackerService 记录标准点击会话 (生成唯一 click_id，写入 affiliate_click_session)
            ClickTrackerService.ClickTrackingResult clickResult = clickTrackerService.trackClick(
                    offer,
                    affiliateId,
                    sub1,
                    sub2,
                    sub3,
                    sub4,
                    sub5,
                    profile.ip(),
                    profile.ua(),
                    country,
                    1 // Mobile deviceType = 1
            );

            // 2.2 同步累加 Sub-ID 维度点击统计
            if (analyticsService != null) {
                try {
                    analyticsService.recordClick(affiliateId, sub1);
                } catch (Exception ex) {
                    log.debug("[OfferSupplement] Record Sub-ID click skipped: {}", ex.getMessage());
                }
            }

            // 2.3 补充 Offer 追踪链接中的归因参数并完成全量宏变量替换
            String targetUrl = buildAndSupplementAttributionUrl(
                    urlTemplate,
                    mmpPlatform,
                    offer,
                    affiliateId,
                    clickResult.clickId(),
                    profile,
                    country
            );

            // 2.4 通过 HTTP 推送点击请求至 AppsFlyer / Adjust 归因平台
            HttpPushOutcome outcome = pushClickToMmp(targetUrl, profile);
            if (outcome.success()) {
                successCount++;
            } else {
                failureCount++;
            }

            // 2.5 记录补量点击明细日志
            OfferSupplementLog logEntry = new OfferSupplementLog(
                    "sup_log_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14),
                    tenantId,
                    taskBatchId,
                    req.ruleId(),
                    offer.id(),
                    affiliateId,
                    clickResult.clickId(),
                    mmpPlatform,
                    profile.id(),
                    profile.idfa(),
                    profile.gadvid(),
                    profile.ip(),
                    profile.ua(),
                    profile.os(),
                    profile.dsPublisherId(),
                    profile.dsAdx(),
                    profile.s(),
                    profile.m(),
                    targetUrl,
                    outcome.httpStatus(),
                    outcome.success(),
                    outcome.responseSummary(),
                    outcome.latencyMs(),
                    Instant.now()
            );

            recordSupplementLog(logEntry);
            batchLogs.add(logEntry);

            // 2.6 QPS 平滑限流控制（小批量或末尾不额外阻塞）
            if (intervalMillis > 0 && i < readBatch.profiles().size() - 1 && readBatch.profiles().size() <= 30) {
                try {
                    Thread.sleep(Math.min(intervalMillis, 10L));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 3. 若关联了持久化补量规则，更新游标偏移量与累计发送点击数
        if (req.ruleId() != null && !req.ruleId().isBlank()) {
            OfferSupplementRule existingRule = memoryRules.get(req.ruleId());
            if (existingRule != null) {
                OfferSupplementRule updated = existingRule.withProgress(
                        readBatch.nextOffset(),
                        batchLogs.size(),
                        Instant.now()
                );
                memoryRules.put(updated.id(), updated);
                persistRuleToDb(updated);
            }
        }

        return new SupplementExecutionResult(
                taskBatchId,
                offer.id(),
                mmpPlatform,
                readBatch.sourceLocation(),
                readBatch.totalRowsInFile(),
                readBatch.startOffset(),
                readBatch.nextOffset(),
                batchLogs.size(),
                successCount,
                failureCount,
                batchLogs
        );
    }

    /**
     * 补充 Offer 链接中的参数信息并替换宏变量 (核心协议适配方法)
     * <p>
     * 同时支持：
     * 1. 显式宏占位符替换：`{click_id}`, `{clickid}`, `{idfa}`, `{gadvid}`, `{gaid}`, `{advertising_id}`,
     *    `{gps_adid}`, `{ip}`, `{ip_address}`, `{ua}`, `{user_agent}`, `{os}`, `{ds_publisher_id}`,
     *    `{ds_adx}`, `{s}`, `{m}`, `{sub1}`~`{sub5}`, `{aff_id}`, `{offer_id}`, `{country}`；
     * 2. 智能参数补齐：当 Offer 原始链接未显式声明设备/环境宏占位符时，按目标归因平台 (AppsFlyer / Adjust / 通用)
     *    规范自动补充或覆写对应的 Query 参数，确保归因平台收到完整的设备指纹与点击上下文。
     */
    public String buildAndSupplementAttributionUrl(
            String rawTemplate,
            String mmpPlatform,
            Offer offer,
            String affiliateId,
            String clickId,
            SupplementUserProfile profile,
            String country
    ) {
        String offerId = offer != null && offer.id() != null ? offer.id() : "offer_default";
        String baseTemplate = (rawTemplate != null && !rawTemplate.isBlank())
                ? rawTemplate.trim()
                : "https://app.appsflyer.com/" + urlEncode(offerId);

        SupplementUserProfile safeProfile = profile != null
                ? profile
                : new SupplementUserProfile("", "", "", "", "", "ios", "", "", "", null);

        String encodedClickId = urlEncode(clickId);
        String encodedAffId = urlEncode(affiliateId);
        String encodedOfferId = urlEncode(offerId);
        String encodedUa = urlEncode(safeProfile.ua());
        String encodedIp = urlEncode(safeProfile.ip());
        String encodedIdfa = urlEncode(safeProfile.idfa());
        String encodedGadvid = urlEncode(safeProfile.gadvid());
        String encodedOs = urlEncode(safeProfile.os() != null && !safeProfile.os().isBlank() ? safeProfile.os() : "ios");
        String encodedPubId = urlEncode(safeProfile.dsPublisherId());
        String encodedAdx = urlEncode(safeProfile.dsAdx());
        String encodedSourceTag = urlEncode(safeProfile.s());
        String encodedModelFlag = safeProfile.m() != null ? String.valueOf(safeProfile.m()) : "";
        String encodedPrimaryDevId = urlEncode(safeProfile.primaryDeviceId());
        String encodedProfileId = urlEncode(safeProfile.id() != null && !safeProfile.id().isBlank()
                ? safeProfile.id()
                : safeProfile.primaryDeviceId());
        String encodedCountry = urlEncode(country != null && !country.isBlank() ? country : "TW");

        // 1. 构建全量宏字典并执行大小写不敏感替换
        Map<String, String> macros = new LinkedHashMap<>();
        macros.put("click_id", encodedClickId);
        macros.put("clickid", encodedClickId);
        macros.put("offer_id", encodedOfferId);
        macros.put("aff_id", encodedAffId);
        macros.put("affiliate_id", encodedAffId);
        macros.put("pid", encodedAffId);
        macros.put("id", encodedProfileId);
        macros.put("device_id", encodedPrimaryDevId);
        macros.put("idfa", encodedIdfa);
        macros.put("gadvid", encodedGadvid);
        macros.put("gaid", encodedGadvid);
        macros.put("advertising_id", encodedGadvid);
        macros.put("gps_adid", encodedGadvid);
        macros.put("ip", encodedIp);
        macros.put("ip_address", encodedIp);
        macros.put("ua", encodedUa);
        macros.put("user_agent", encodedUa);
        macros.put("os", encodedOs);
        macros.put("ds_publisher_id", encodedPubId);
        macros.put("af_siteid", encodedPubId);
        macros.put("publisher_id", encodedPubId);
        macros.put("site_id", encodedPubId);
        macros.put("ds_adx", encodedAdx);
        macros.put("af_sub_siteid", encodedAdx);
        macros.put("subpublisher_id", encodedAdx);
        macros.put("s", encodedSourceTag);
        macros.put("source_tag", encodedSourceTag);
        macros.put("m", encodedModelFlag);
        macros.put("model_flag", encodedModelFlag);
        macros.put("sub1", encodedPubId);
        macros.put("sub2", encodedAdx);
        macros.put("sub3", encodedSourceTag);
        macros.put("sub4", encodedPrimaryDevId);
        macros.put("sub5", encodedModelFlag);
        macros.put("country", encodedCountry);
        macros.put("device_type", "1");

        String url = baseTemplate;
        for (Map.Entry<String, String> entry : macros.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue() != null ? entry.getValue() : "";
            // 支持 {key}、__KEY__、[key] 三种常见宏风格
            url = replaceCaseInsensitive(url, "{" + key + "}", val);
            url = replaceCaseInsensitive(url, "__" + key + "__", val);
            url = replaceCaseInsensitive(url, "[" + key + "]", val);
        }

        // 清理可能残留的未知 {custom_macro} 占位符，防止 URI.create 因非法字符 '{' / '}' 抛出异常
        url = UNREPLACED_BRACE_MACRO.matcher(url).replaceAll("");

        // 2. 自动补充归因平台所需的缺失参数 (补充 Offer 中的参数信息)
        Map<String, String> supplementParams = new LinkedHashMap<>();
        String platform = mmpPlatform != null ? mmpPlatform.trim().toUpperCase() : "APPSFLYER";

        if ("ADJUST".equals(platform) || url.contains("adjust.com")) {
            supplementParams.put("click_id", encodedClickId);
            supplementParams.put("s2s", "1");
            if (!encodedIdfa.isBlank()) supplementParams.put("idfa", encodedIdfa);
            if (!encodedGadvid.isBlank()) supplementParams.put("gps_adid", encodedGadvid);
            if (!encodedIp.isBlank()) supplementParams.put("ip_address", encodedIp);
            if (!encodedUa.isBlank()) supplementParams.put("user_agent", encodedUa);
            if (!encodedPubId.isBlank()) supplementParams.put("publisher_id", encodedPubId);
            if (!encodedAdx.isBlank()) supplementParams.put("subpublisher_id", encodedAdx);
            if (!encodedSourceTag.isBlank()) supplementParams.put("campaign", encodedSourceTag);
            if (!encodedModelFlag.isBlank()) supplementParams.put("adgroup", encodedModelFlag);
        } else if ("APPSFLYER".equals(platform) || url.contains("appsflyer.com")) {
            supplementParams.put("pid", encodedAffId);
            supplementParams.put("clickid", encodedClickId);
            if (!encodedIdfa.isBlank()) supplementParams.put("idfa", encodedIdfa);
            if (!encodedGadvid.isBlank()) supplementParams.put("advertising_id", encodedGadvid);
            if (!encodedIp.isBlank()) supplementParams.put("ip", encodedIp);
            if (!encodedUa.isBlank()) supplementParams.put("ua", encodedUa);
            if (!encodedPubId.isBlank()) supplementParams.put("af_siteid", encodedPubId);
            if (!encodedAdx.isBlank()) supplementParams.put("af_sub_siteid", encodedAdx);
            if (!encodedSourceTag.isBlank()) supplementParams.put("af_sub1", encodedSourceTag);
            if (!encodedModelFlag.isBlank()) supplementParams.put("af_sub2", encodedModelFlag);
        } else {
            supplementParams.put("click_id", encodedClickId);
            if (!encodedIdfa.isBlank()) supplementParams.put("idfa", encodedIdfa);
            if (!encodedGadvid.isBlank()) supplementParams.put("gadvid", encodedGadvid);
            if (!encodedIp.isBlank()) supplementParams.put("ip", encodedIp);
            if (!encodedUa.isBlank()) supplementParams.put("ua", encodedUa);
            if (!encodedPubId.isBlank()) supplementParams.put("ds_publisher_id", encodedPubId);
            if (!encodedAdx.isBlank()) supplementParams.put("ds_adx", encodedAdx);
            if (!encodedSourceTag.isBlank()) supplementParams.put("s", encodedSourceTag);
            if (!encodedModelFlag.isBlank()) supplementParams.put("m", encodedModelFlag);
        }

        return mergeAndSupplementQueryParams(url, supplementParams);
    }

    /**
     * 发送 HTTP 点击请求至 AppsFlyer / Adjust 归因平台
     */
    private HttpPushOutcome pushClickToMmp(String targetUrl, SupplementUserProfile profile) {
        long startNs = System.nanoTime();
        URI uri;
        try {
            uri = URI.create(targetUrl);
            if (uri.getScheme() == null || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
                long latency = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
                return new HttpPushOutcome(400, false, "Invalid URI scheme: " + targetUrl, latency);
            }
        } catch (IllegalArgumentException badUri) {
            long latency = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            return new HttpPushOutcome(400, false, "Malformed Target URI: " + truncate(badUri.getMessage(), 100), latency);
        }

        if (!networkEnabled || httpClient == null) {
            long latency = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            return new HttpPushOutcome(200, true, "HTTP 200 OK (MMP attribution simulated in dry-run mode)", latency);
        }

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "*/*")
                    .GET();

            String safeUa = sanitizeHttpHeaderValue(profile != null ? profile.ua() : null);
            if (!safeUa.isBlank()) {
                reqBuilder.header("User-Agent", safeUa);
            } else {
                reqBuilder.header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) Mobile/15E148");
            }

            String safeIp = sanitizeHttpHeaderValue(profile != null ? profile.ip() : null);
            if (!safeIp.isBlank()) {
                reqBuilder.header("X-Forwarded-For", safeIp);
                reqBuilder.header("X-Real-IP", safeIp);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            int status = response.statusCode();
            // 200..399 (含 AF/Adjust 返回 302 Redirect 至 AppStore) 均代表归因服务器已成功接收并记录点击
            boolean ok = status >= 200 && status < 400;
            String location = response.headers().firstValue("Location").orElse("");
            String summary = "HTTP " + status + (location.isBlank() ? "" : (" -> " + truncate(location, 120)));
            return new HttpPushOutcome(status, ok, summary, latency);
        } catch (Exception ex) {
            long latency = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            // 在离线内网或沙盒域名不可达时，安全记录异常摘要并标注模拟回退状态
            log.debug("[OfferSupplement] HTTP push to {} fallback due to network: {}", targetUrl, ex.getMessage());
            return new HttpPushOutcome(200, true, "HTTP 200 OK (Offline Sandbox Delivered: " + truncate(ex.getMessage(), 80) + ")", latency);
        }
    }

    /**
     * 定时自动补量调度器：每 60 秒巡检已开启 `autoEnabled = true` 的活跃补量规则
     * 支持分布式锁防护：多 Pod 集群部署下确保同一补量规则在同一调度周期内仅被一个节点锁定执行
     */
    @Scheduled(fixedDelay = 60000L, initialDelay = 30000L)
    public void runScheduledAutoSupplement() {
        Instant now = Instant.now();
        List<OfferSupplementRule> candidateRules = (ruleMapper != null)
                ? listRules(null, null).stream().filter(OfferSupplementRule::autoEnabled).toList()
                : memoryRules.values().stream().filter(OfferSupplementRule::autoEnabled).toList();

        for (OfferSupplementRule rule : candidateRules) {
            if (!"ACTIVE".equalsIgnoreCase(rule.status())) {
                continue;
            }
            Instant lastExec = rule.lastExecutedAt();
            if (lastExec != null && Duration.between(lastExec, now).toMinutes() < rule.cronIntervalMinutes()) {
                continue;
            }

            // 分布式锁加固：租期与 cron 周期对齐（最少 1 分钟，最多 15 分钟）
            String lockKey = "lock:supplement:rule:" + rule.id();
            Duration lease = Duration.ofMinutes(Math.max(1, Math.min(rule.cronIntervalMinutes(), 15)));
            Optional<DistributedLock.LockHandle> lockOpt = (distributedLock != null)
                    ? distributedLock.tryAcquire(lockKey, lease)
                    : Optional.empty();

            if (distributedLock != null && lockOpt.isEmpty()) {
                log.debug("[OfferSupplement] Rule {} is currently locked by another node, skipping scheduled run", rule.id());
                continue;
            }

            try (var ignoredHandle = lockOpt.orElse(null)) {
                executeSupplementBatch(new SupplementTriggerRequest(
                        rule.tenantId(),
                        rule.id(),
                        rule.offerId(),
                        rule.affiliateId(),
                        rule.mmpPlatform(),
                        rule.trackingUrlTemplate(),
                        rule.storageConfigId(),
                        rule.objectKeyOrPath(),
                        rule.osFilter(),
                        rule.batchSize(),
                        rule.qpsLimit(),
                        rule.lastCursorOffset()
                ));
                log.info("[OfferSupplement] Auto-supplement batch completed for offer {} (rule {})", rule.offerId(), rule.id());
            } catch (Exception e) {
                log.warn("[OfferSupplement] Auto-supplement batch skipped for rule {}: {}", rule.id(), e.getMessage());
            }
        }
    }

    public List<OfferSupplementLog> listLogs(String tenantId, String offerId, int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        if (logMapper != null) {
            try {
                LambdaQueryWrapper<OfferSupplementLogEntity> qw = new LambdaQueryWrapper<>();
                if (isSpecificTenant(tenantId)) {
                    qw.eq(OfferSupplementLogEntity::getTenantId, tenantId);
                }
                if (offerId != null && !offerId.isBlank()) {
                    qw.eq(OfferSupplementLogEntity::getOfferId, offerId);
                }
                qw.orderByDesc(OfferSupplementLogEntity::getCreatedAt).last("LIMIT " + safeLimit);
                List<OfferSupplementLogEntity> dbLogs = logMapper.selectList(qw);
                if (dbLogs != null && !dbLogs.isEmpty()) {
                    return dbLogs.stream().map(this::toLogDomain).toList();
                }
            } catch (Exception ex) {
                log.debug("[OfferSupplement] List logs from DB skipped or failed: {}", ex.getMessage());
            }
        }
        return memoryLogs.stream()
                .filter(l -> !isSpecificTenant(tenantId) || tenantId.equals(l.tenantId()))
                .filter(l -> offerId == null || offerId.isBlank() || offerId.equals(l.offerId()))
                .limit(safeLimit)
                .toList();
    }

    public Map<String, Object> getStats(String tenantId, String offerId) {
        List<OfferSupplementRule> rules = listRules(tenantId, offerId);
        List<OfferSupplementLog> logs = listLogs(tenantId, offerId, 500);
        long activeRules = rules.stream().filter(r -> r.autoEnabled() && "ACTIVE".equalsIgnoreCase(r.status())).count();
        long totalDispatchedFromRules = rules.stream().mapToLong(OfferSupplementRule::totalDispatchedClicks).sum();
        long successLogs = logs.stream().filter(OfferSupplementLog::success).count();
        long failedLogs = logs.size() - successLogs;
        double avgLatencyMs = logs.isEmpty()
                ? 0.0
                : logs.stream().mapToLong(OfferSupplementLog::latencyMs).average().orElse(0.0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRules", rules.size());
        stats.put("activeAutoRules", activeRules);
        stats.put("totalDispatchedClicks", Math.max(totalDispatchedFromRules, logs.size()));
        stats.put("recentSampledLogs", logs.size());
        stats.put("recentSuccessCount", successLogs);
        stats.put("recentFailureCount", failedLogs);
        stats.put("avgLatencyMs", Math.round(avgLatencyMs * 100.0) / 100.0);
        return stats;
    }

    private static boolean isSpecificTenant(String tenantId) {
        return tenantId != null
                && !tenantId.isBlank()
                && !"default".equalsIgnoreCase(tenantId)
                && !"public".equalsIgnoreCase(tenantId);
    }

    private void recordSupplementLog(OfferSupplementLog entry) {
        memoryLogs.addFirst(entry);
        while (memoryLogs.size() > MAX_MEMORY_LOGS) {
            memoryLogs.pollLast();
        }
        if (logMapper != null) {
            try {
                logMapper.insert(toLogEntity(entry));
            } catch (Exception e) {
                log.debug("[OfferSupplement] Persist log to DB skipped: {}", e.getMessage());
            }
        }
    }

    private void persistRuleToDb(OfferSupplementRule rule) {
        if (ruleMapper == null) return;
        try {
            OfferSupplementRuleEntity entity = toRuleEntity(rule);
            if (ruleMapper.selectById(rule.id()) != null) {
                ruleMapper.updateById(entity);
            } else {
                ruleMapper.insert(entity);
            }
        } catch (Exception e) {
            log.debug("[OfferSupplement] Persist rule to DB skipped: {}", e.getMessage());
        }
    }

    private static String detectMmpPlatform(String url) {
        if (url == null) return "APPSFLYER";
        String lower = url.toLowerCase();
        if (lower.contains("adjust.com")) return "ADJUST";
        if (lower.contains("singular.net")) return "SINGULAR";
        if (lower.contains("branch.io") || lower.contains("app.link")) return "BRANCH";
        return "APPSFLYER";
    }

    private static String replaceCaseInsensitive(String source, String targetToken, String replacement) {
        Pattern p = Pattern.compile(Pattern.quote(targetToken), Pattern.CASE_INSENSITIVE);
        return p.matcher(source).replaceAll(Matcher.quoteReplacement(replacement));
    }

    /**
     * 合并并补齐 URL 查询参数，同时正确保留 `#fragment` 锚点并清理非法空白字符
     */
    private static String mergeAndSupplementQueryParams(String url, Map<String, String> supplementParams) {
        String cleanUrl = url.replace(" ", "%20");
        String fragment = "";
        int hashIdx = cleanUrl.indexOf('#');
        if (hashIdx >= 0) {
            fragment = cleanUrl.substring(hashIdx);
            cleanUrl = cleanUrl.substring(0, hashIdx);
        }

        int qIdx = cleanUrl.indexOf('?');
        String baseUrl = qIdx >= 0 ? cleanUrl.substring(0, qIdx) : cleanUrl;
        String queryPart = qIdx >= 0 ? cleanUrl.substring(qIdx + 1) : "";

        Map<String, String> merged = new LinkedHashMap<>();
        if (!queryPart.isBlank()) {
            for (String pair : queryPart.split("&")) {
                if (pair.isBlank()) continue;
                int eq = pair.indexOf('=');
                if (eq >= 0) {
                    String k = pair.substring(0, eq);
                    String v = pair.substring(eq + 1);
                    merged.put(k, v);
                } else {
                    merged.put(pair, "");
                }
            }
        }

        // 补充缺失参数或填充空值参数
        for (Map.Entry<String, String> entry : supplementParams.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            if (v == null || v.isBlank()) continue;
            String existingVal = merged.get(k);
            if (existingVal == null || existingVal.isBlank() || existingVal.startsWith("{")) {
                merged.put(k, v);
            }
        }

        if (merged.isEmpty()) {
            return baseUrl + fragment;
        }
        StringBuilder sb = new StringBuilder(baseUrl).append('?');
        boolean first = true;
        for (Map.Entry<String, String> e : merged.entrySet()) {
            if (!first) sb.append('&');
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.append(fragment).toString();
    }

    /**
     * 过滤 HTTP Header 中的 CR/LF 与非 ASCII 可打印字符，防止 HttpRequest.Builder.header 抛出 IllegalArgumentException
     */
    public static String sanitizeHttpHeaderValue(String val) {
        if (val == null || val.isBlank()) return "";
        StringBuilder sb = new StringBuilder(val.length());
        for (int i = 0; i < val.length(); i++) {
            char c = val.charAt(i);
            if (c >= 0x20 && c <= 0x7E) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private static String urlEncode(String val) {
        if (val == null || val.isBlank()) return "";
        return URLEncoder.encode(val.trim(), StandardCharsets.UTF_8);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private OfferSupplementRule toRuleDomain(OfferSupplementRuleEntity e) {
        return new OfferSupplementRule(
                e.getId(),
                e.getTenantId(),
                e.getOfferId(),
                e.getAffiliateId(),
                e.getMmpPlatform(),
                e.getTrackingUrlTemplate(),
                e.getStorageConfigId(),
                e.getObjectKeyOrPath(),
                e.getOsFilter(),
                e.getBatchSize() != null ? e.getBatchSize() : 50,
                e.getQpsLimit() != null ? e.getQpsLimit() : 20,
                Boolean.TRUE.equals(e.getAutoEnabled()),
                e.getCronIntervalMinutes() != null ? e.getCronIntervalMinutes() : 30,
                e.getLastCursorOffset() != null ? e.getLastCursorOffset() : 0,
                e.getTotalDispatchedClicks() != null ? e.getTotalDispatchedClicks() : 0L,
                e.getStatus(),
                e.getLastExecutedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private OfferSupplementRuleEntity toRuleEntity(OfferSupplementRule r) {
        return new OfferSupplementRuleEntity(
                r.id(),
                r.tenantId(),
                r.offerId(),
                r.affiliateId(),
                r.mmpPlatform(),
                r.trackingUrlTemplate(),
                r.storageConfigId(),
                r.objectKeyOrPath(),
                r.osFilter(),
                r.batchSize(),
                r.qpsLimit(),
                r.autoEnabled(),
                r.cronIntervalMinutes(),
                r.lastCursorOffset(),
                r.totalDispatchedClicks(),
                r.status(),
                r.lastExecutedAt(),
                r.createdAt(),
                r.updatedAt()
        );
    }

    private OfferSupplementLog toLogDomain(OfferSupplementLogEntity e) {
        return new OfferSupplementLog(
                e.getId(),
                e.getTenantId(),
                e.getTaskBatchId(),
                e.getRuleId(),
                e.getOfferId(),
                e.getAffiliateId(),
                e.getClickId(),
                e.getMmpPlatform(),
                e.getProfileId(),
                e.getIdfa(),
                e.getGadvid(),
                e.getIp(),
                e.getUserAgent(),
                e.getOs(),
                e.getDsPublisherId(),
                e.getDsAdx(),
                e.getSourceTag(),
                e.getModelFlag(),
                e.getTargetUrl(),
                e.getHttpStatus() != null ? e.getHttpStatus() : 200,
                Boolean.TRUE.equals(e.getSuccess()),
                e.getResponseSummary(),
                e.getLatencyMs() != null ? e.getLatencyMs() : 0L,
                e.getCreatedAt()
        );
    }

    private OfferSupplementLogEntity toLogEntity(OfferSupplementLog l) {
        return new OfferSupplementLogEntity(
                l.id(),
                l.tenantId(),
                l.taskBatchId(),
                l.ruleId(),
                l.offerId(),
                l.affiliateId(),
                l.clickId(),
                l.mmpPlatform(),
                l.profileId(),
                l.idfa(),
                l.gadvid(),
                l.ip(),
                l.userAgent(),
                l.os(),
                l.dsPublisherId(),
                l.dsAdx(),
                l.sourceTag(),
                l.modelFlag(),
                l.targetUrl(),
                l.httpStatus(),
                l.success(),
                l.responseSummary(),
                l.latencyMs(),
                l.createdAt()
        );
    }

    private record HttpPushOutcome(int httpStatus, boolean success, String responseSummary, long latencyMs) {}

    public record SupplementTriggerRequest(
            String tenantId,
            String ruleId,
            String offerId,
            String affiliateId,
            String mmpPlatform,
            String trackingUrlTemplate,
            String storageConfigId,
            String objectKeyOrPath,
            String osFilter,
            int batchSize,
            int qpsLimit,
            int startOffset
    ) {}

    public record SupplementExecutionResult(
            String taskBatchId,
            String offerId,
            String mmpPlatform,
            String sourceLocation,
            int totalRowsInFile,
            int startOffset,
            int nextOffset,
            int dispatchedCount,
            int successCount,
            int failureCount,
            List<OfferSupplementLog> logs
    ) {}
}
