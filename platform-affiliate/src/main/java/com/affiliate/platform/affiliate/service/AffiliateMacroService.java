package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.MacroParam;
import com.affiliate.platform.affiliate.domain.PlatformMacroMapping;
import com.affiliate.platform.entity.MacroParamEntity;
import com.affiliate.platform.entity.PlatformMacroMappingEntity;
import com.affiliate.platform.mapper.MacroParamMapper;
import com.affiliate.platform.mapper.PlatformMacroMappingMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 宏参数与平台映射管理服务 (Macro Parameter & Platform Mapping Service - MyBatis-Plus)
 * <p>
 * 维护本平台标准宏字典，以及各广告/流量平台 (Awin、ShareASale、CJ、TikTok 等) 的宏写法对照表，
 * 提供追踪链接与 S2S 回传链接在「标准宏 ⇄ 平台宏」之间的双向渲染转换。
 * 宏配置为平台级全局字典，跨租户共享。
 */
@Service
public class AffiliateMacroService {

    private static final Logger log = LoggerFactory.getLogger(AffiliateMacroService.class);

    private final MacroParamMapper macroParamMapper;
    private final PlatformMacroMappingMapper mappingMapper;

    private volatile boolean dbAvailable = false;

    private final ConcurrentMap<String, MacroParam> fallbackParams = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PlatformMacroMapping> fallbackMappings = new ConcurrentHashMap<>();

    public AffiliateMacroService() {
        this(null, null);
    }

    @Autowired
    public AffiliateMacroService(
            @Autowired(required = false) MacroParamMapper macroParamMapper,
            @Autowired(required = false) PlatformMacroMappingMapper mappingMapper
    ) {
        this.macroParamMapper = macroParamMapper;
        this.mappingMapper = mappingMapper;
        ensureSeedData();
    }

    // ==========================================
    // 标准宏字典 CRUD
    // ==========================================

    public List<MacroParam> listParams() {
        if (macroParamMapper != null && dbAvailable) {
            try {
                QueryWrapper<MacroParamEntity> qw = new QueryWrapper<>();
                qw.orderByAsc("category").orderByAsc("macro_key");
                return macroParamMapper.selectList(qw).stream().map(this::toParamDomain).toList();
            } catch (Exception e) {
                log.warn("[AffiliateMacroService] Database query listParams failed, fallback to in-memory: {}", e.getMessage());
            }
        }
        return fallbackParams.values().stream()
                .sorted(Comparator.comparing(MacroParam::category).thenComparing(MacroParam::macroKey))
                .toList();
    }

    public MacroParam saveParam(MacroParam param) {
        String key = normalizeKey(param.macroKey());
        if (key.isEmpty()) {
            throw new IllegalArgumentException("宏键 (macroKey) 不能为空");
        }
        boolean dup = listParams().stream()
                .anyMatch(p -> key.equalsIgnoreCase(p.macroKey())
                        && (param.id() == null || !p.id().equals(param.id())));
        if (dup) {
            throw new IllegalArgumentException("标准宏键已存在: " + key);
        }

        String id = param.id();
        if (id == null || id.isBlank()) {
            id = "mcp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        MacroParam saved = new MacroParam(id, key, param.displayName(), param.description(),
                param.sampleValue(), param.category(), param.status(),
                param.createdAt() != null ? param.createdAt() : Instant.now());

        if (macroParamMapper != null && dbAvailable) {
            try {
                MacroParamEntity entity = new MacroParamEntity(
                        saved.id(), saved.macroKey(), saved.displayName(), saved.description(),
                        saved.sampleValue(), saved.category(), saved.status(), saved.createdAt());
                if (macroParamMapper.selectById(saved.id()) != null) {
                    macroParamMapper.updateById(entity);
                } else {
                    macroParamMapper.insert(entity);
                }
                fallbackParams.put(saved.id(), saved);
                return saved;
            } catch (Exception e) {
                log.error("[AffiliateMacroService] Database saveParam failed: {}", e.getMessage(), e);
                throw new IllegalStateException("保存标准宏至数据库失败: " + e.getMessage(), e);
            }
        }
        fallbackParams.put(saved.id(), saved);
        return saved;
    }

    public boolean deleteParam(String id) {
        if (macroParamMapper != null && dbAvailable) {
            try {
                // 查询外提：进入循环前单次查询待删除实体，判断存在性并提取宏键
                MacroParamEntity e = macroParamMapper.selectById(id);
                if (e == null) {
                    return false;
                }
                String macroKey = e.getMacroKey();
                List<PlatformMacroMapping> refs = listMappings(null).stream()
                        .filter(m -> m.macroKey().equalsIgnoreCase(macroKey))
                        .toList();
                if (!refs.isEmpty()) {
                    throw new IllegalArgumentException("该宏仍被 " + refs.size() + " 条平台映射引用，请先删除相关映射");
                }
                boolean deleted = macroParamMapper.deleteById(id) > 0;
                if (deleted) {
                    fallbackParams.remove(id);
                }
                return deleted;
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("[AffiliateMacroService] Database deleteParam failed: {}", e.getMessage(), e);
                throw new IllegalStateException("从数据库删除标准宏失败: " + e.getMessage(), e);
            }
        }
        MacroParam p = fallbackParams.get(id);
        if (p == null) {
            return false;
        }
        String macroKey = p.macroKey();
        List<PlatformMacroMapping> refs = fallbackMappings.values().stream()
                .filter(m -> m.macroKey().equalsIgnoreCase(macroKey))
                .toList();
        if (!refs.isEmpty()) {
            throw new IllegalArgumentException("该宏仍被 " + refs.size() + " 条平台映射引用，请先删除相关映射");
        }
        return fallbackParams.remove(id) != null;
    }

    // ==========================================
    // 平台映射 CRUD
    // ==========================================

    public List<PlatformMacroMapping> listMappings(String platformCode) {
        if (mappingMapper != null && dbAvailable) {
            try {
                QueryWrapper<PlatformMacroMappingEntity> qw = new QueryWrapper<>();
                if (platformCode != null && !platformCode.isBlank()) {
                    qw.eq("platform_code", platformCode.trim().toUpperCase());
                }
                qw.orderByAsc("platform_code").orderByAsc("macro_key");
                return mappingMapper.selectList(qw).stream().map(this::toMappingDomain).toList();
            } catch (Exception e) {
                log.warn("[AffiliateMacroService] Database query listMappings failed, fallback to in-memory: {}", e.getMessage());
            }
        }
        return fallbackMappings.values().stream()
                .filter(m -> platformCode == null || platformCode.isBlank()
                        || m.platformCode().equalsIgnoreCase(platformCode.trim()))
                .sorted(Comparator.comparing(PlatformMacroMapping::platformCode)
                        .thenComparing(PlatformMacroMapping::macroKey))
                .toList();
    }

    public PlatformMacroMapping saveMapping(PlatformMacroMapping mapping) {
        String code = mapping.platformCode() == null ? "" : mapping.platformCode().trim().toUpperCase();
        String key = normalizeKey(mapping.macroKey());
        if (code.isEmpty() || mapping.platformMacroToken() == null || mapping.platformMacroToken().isBlank()) {
            throw new IllegalArgumentException("平台代码与平台宏写法不能为空");
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("标准宏键不能为空");
        }
        boolean conflict = listMappings(code).stream()
                .anyMatch(m -> m.macroKey().equalsIgnoreCase(key)
                        && (mapping.id() == null || !m.id().equals(mapping.id())));
        if (conflict) {
            throw new IllegalArgumentException("平台 " + code + " 下宏 " + key + " 已存在映射");
        }

        String id = mapping.id();
        if (id == null || id.isBlank()) {
            id = "pmm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        PlatformMacroMapping saved = new PlatformMacroMapping(
                id, code, mapping.platformName(), key, mapping.platformMacroToken().trim(),
                mapping.remark(), mapping.status(),
                mapping.createdAt() != null ? mapping.createdAt() : Instant.now(), Instant.now());

        if (mappingMapper != null && dbAvailable) {
            try {
                PlatformMacroMappingEntity entity = new PlatformMacroMappingEntity(
                        saved.id(), saved.platformCode(), saved.platformName(), saved.macroKey(),
                        saved.platformMacroToken(), saved.remark(), saved.status(),
                        saved.createdAt(), saved.updatedAt());
                if (mappingMapper.selectById(saved.id()) != null) {
                    mappingMapper.updateById(entity);
                } else {
                    mappingMapper.insert(entity);
                }
                fallbackMappings.put(saved.id(), saved);
                return saved;
            } catch (Exception e) {
                log.error("[AffiliateMacroService] Database saveMapping failed: {}", e.getMessage(), e);
                throw new IllegalStateException("保存平台宏映射至数据库失败: " + e.getMessage(), e);
            }
        }
        fallbackMappings.put(saved.id(), saved);
        return saved;
    }

    public boolean deleteMapping(String id) {
        if (mappingMapper != null && dbAvailable) {
            try {
                boolean deleted = mappingMapper.deleteById(id) > 0;
                if (deleted) {
                    fallbackMappings.remove(id);
                }
                return deleted;
            } catch (Exception e) {
                log.error("[AffiliateMacroService] Database deleteMapping failed: {}", e.getMessage(), e);
                throw new IllegalStateException("从数据库删除平台宏映射失败: " + e.getMessage(), e);
            }
        }
        return fallbackMappings.remove(id) != null;
    }

    /** 平台维度汇总: 代码、名称、映射数量 */
    public List<Map<String, Object>> listPlatforms() {
        Map<String, List<PlatformMacroMapping>> grouped = new TreeMap<>();
        for (PlatformMacroMapping m : listMappings(null)) {
            grouped.computeIfAbsent(m.platformCode(), k -> new ArrayList<>()).add(m);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((code, list) -> result.add(Map.of(
                "platformCode", code,
                "platformName", list.get(0).platformName() == null ? code : list.get(0).platformName(),
                "mappingCount", list.size()
        )));
        return result;
    }

    // ==========================================
    // 链接双向渲染
    // ==========================================

    /**
     * 渲染方向: 标准宏 → 平台宏
     * 例: https://advertiser.com/land?clickid={click_id}&u1={sub1} → AWIN 写法
     */
    public RenderResult renderToPlatform(String platformCode, String url) {
        return convert(platformCode, url, true);
    }

    /**
     * 反渲染方向: 平台宏 → 标准宏
     * 例: 广告主提供的含平台原生宏的链接 → 本平台标准追踪链接模板
     */
    public RenderResult renderToStandard(String platformCode, String url) {
        return convert(platformCode, url, false);
    }

    private RenderResult convert(String platformCode, String url, boolean toPlatform) {
        if (platformCode == null || platformCode.isBlank()) {
            throw new IllegalArgumentException("请选择目标平台");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("待转换链接不能为空");
        }
        List<PlatformMacroMapping> mappings = listMappings(platformCode).stream()
                .filter(m -> "ACTIVE".equalsIgnoreCase(m.status()))
                .toList();
        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("平台 " + platformCode.toUpperCase() + " 尚无生效的宏映射");
        }

        Set<String> knownStandard = new HashSet<>();
        listParams().forEach(p -> knownStandard.add(p.macroKey().toLowerCase()));

        String result = url;
        List<Map<String, String>> replaced = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();

        if (toPlatform) {
            // 标准 → 平台: 按标准宏 token 长度倒序排序，避免短子串误伤长占位符（如 {sub1} 误伤 {sub10}）
            List<PlatformMacroMapping> sortedMappings = mappings.stream()
                    .sorted(Comparator.comparingInt((PlatformMacroMapping m) -> ("{" + m.macroKey() + "}").length()).reversed())
                    .toList();

            for (PlatformMacroMapping m : sortedMappings) {
                String standardToken = "{" + m.macroKey() + "}";
                Pattern pattern = Pattern.compile(Pattern.quote(standardToken), Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(result);
                if (matcher.find()) {
                    result = matcher.replaceAll(Matcher.quoteReplacement(m.platformMacroToken()));
                    // 恒等映射不计入 replaced（例如 {country} 转为 {country} 无实质形态转换）
                    if (!standardToken.equalsIgnoreCase(m.platformMacroToken())) {
                        replaced.add(Map.of("macroKey", m.macroKey(), "token", m.platformMacroToken()));
                    }
                }
            }
            for (String token : extractBraceTokens(result)) {
                if (knownStandard.contains(token.toLowerCase())
                        && replaced.stream().noneMatch(r -> r.get("macroKey").equalsIgnoreCase(token))) {
                    unmapped.add(token);
                }
            }
        } else {
            // 平台 → 标准 (standardize): 按平台宏 token 长度倒序排序，避免短子串误伤长占位符（如 [subid] 误伤 [subid_extra]）
            List<PlatformMacroMapping> sortedMappings = mappings.stream()
                    .sorted(Comparator.comparingInt((PlatformMacroMapping m) -> m.platformMacroToken().length()).reversed())
                    .toList();

            for (PlatformMacroMapping m : sortedMappings) {
                // 反向匹配大小写归一：无论传入 [ssn]、[SSN] 还是 __clickid__、__CLICKID__，均可不敏感匹配并替换为标准宏
                Pattern pattern = Pattern.compile(Pattern.quote(m.platformMacroToken()), Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(result);
                if (matcher.find()) {
                    String standardToken = "{" + m.macroKey() + "}";
                    result = matcher.replaceAll(Matcher.quoteReplacement(standardToken));
                    // 恒等映射不计入 replaced
                    if (!m.platformMacroToken().equalsIgnoreCase(standardToken)) {
                        replaced.add(Map.of("macroKey", m.macroKey(), "token", m.platformMacroToken()));
                    }
                }
            }

            // standardize 补 unmapped 反馈：扫描转换后仍残留的未映射宏占位符（支持 {...}, [...], __...__ 等常见平台宏风格）
            for (String token : extractPotentialMacroTokens(result)) {
                String stripped = token.replaceAll("^[\\[{_]+|[\\]}_]+$", "");
                // 如果是已识别的标准宏占位符形如 {macro_key}，说明已标准化或本身就是标准宏，不计入 unmapped
                if (token.startsWith("{") && token.endsWith("}") && knownStandard.contains(stripped.toLowerCase())) {
                    continue;
                }
                if (!unmapped.contains(token)) {
                    unmapped.add(token);
                }
            }
        }
        return new RenderResult(result, replaced, unmapped);
    }

    /** 提取 URL 中形如 {xxx}, [xxx], __XXX__ 的宏占位候选词 */
    private List<String> extractPotentialMacroTokens(String url) {
        List<String> tokens = new ArrayList<>();
        // 1. 方括号 [xxx]
        Matcher mBrackets = Pattern.compile("\\[([^\\[\\]]+)\\]").matcher(url);
        while (mBrackets.find()) {
            tokens.add(mBrackets.group(0));
        }
        // 2. 双下划线 __XXX__
        Matcher mUnderscores = Pattern.compile("(__[A-Za-z0-9_]+__)").matcher(url);
        while (mUnderscores.find()) {
            tokens.add(mUnderscores.group(0));
        }
        // 3. 花括号 {xxx}
        Matcher mBraces = Pattern.compile("\\{([^{}]+)\\}").matcher(url);
        while (mBraces.find()) {
            tokens.add(mBraces.group(0));
        }
        return tokens;
    }

    /** 提取 URL 中剩余的 {xxx} 宏占位符 (不含 {}) */
    private List<String> extractBraceTokens(String url) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < url.length()) {
            int open = url.indexOf('{', i);
            if (open < 0) break;
            int close = url.indexOf('}', open + 1);
            if (close < 0) break;
            String inner = url.substring(open + 1, close).trim();
            if (!inner.isEmpty()) tokens.add(inner);
            i = close + 1;
        }
        return tokens;
    }

    public record RenderResult(String convertedUrl, List<Map<String, String>> replaced, List<String> unmapped) {}

    // ==========================================
    // 种子数据与启动保护
    // ==========================================

    private void ensureSeedData() {
        // 先在内存中构建完备的默认种子字典，确保表未创建、未迁移或离线模式下系统直接高可用
        initFallbackSeed();

        if (macroParamMapper != null && mappingMapper != null) {
            try {
                Long paramCount = macroParamMapper.selectCount(null);
                Long mappingCount = mappingMapper.selectCount(null);
                dbAvailable = true;
                if (paramCount != null && paramCount == 0) {
                    initDbSeedParams();
                }
                if (mappingCount != null && mappingCount == 0) {
                    initDbSeedMappings();
                }
            } catch (Exception e) {
                dbAvailable = false;
                // 关键保护：缺 V12 表或数据库无法访问时，记录告警并安全降级，绝不阻塞应用上下文启动
                log.warn("[AffiliateMacroService] Macro tables (V12) not ready or query failed, graceful fallback to in-memory seeds. Details: {}", e.getMessage());
            }
        } else {
            dbAvailable = false;
        }
    }

    private void initFallbackSeed() {
        if (fallbackParams.isEmpty()) {
            seedFallbackParam("click_id", "点击唯一标识", "点击追踪会话 ID，归因与防重付的核心主键", "c_8f3a2b1c", "ATTRIBUTION");
            seedFallbackParam("sub1", "子渠道标识 1", "渠道自定义流量分层参数 (媒体/版位/创意)", "fb_campaign_01", "SUB_TRACKING");
            seedFallbackParam("sub2", "子渠道标识 2", "渠道自定义流量分层参数", "banner_eu", "SUB_TRACKING");
            seedFallbackParam("sub3", "子渠道标识 3", "渠道自定义流量分层参数", null, "SUB_TRACKING");
            seedFallbackParam("sub4", "子渠道标识 4", "渠道自定义流量分层参数", null, "SUB_TRACKING");
            seedFallbackParam("sub5", "子渠道标识 5", "渠道自定义流量分层参数", null, "SUB_TRACKING");
            seedFallbackParam("txid", "广告主订单流水号", "S2S 回传订单唯一号 (幂等去重键)", "ORD_20260918_001", "TRANSACTION");
            seedFallbackParam("payout", "渠道应佣金额", "回传给渠道的结算佣金", "12.50", "TRANSACTION");
            seedFallbackParam("revenue", "订单收入金额", "广告主侧订单金额", "99.00", "TRANSACTION");
            seedFallbackParam("country", "访客国家", "点击来源国家 ISO 码", "US", "ENVIRONMENT");
            seedFallbackParam("os", "操作系统", "访客设备操作系统", "ios", "ENVIRONMENT");
            seedFallbackParam("device_type", "设备类型", "1=Mobile 2=Desktop 3=Tablet", "1", "ENVIRONMENT");
            seedFallbackParam("ip", "访客 IP", "点击发生时的客户端 IP", "203.0.113.7", "ENVIRONMENT");
            seedFallbackParam("ua", "User-Agent", "点击发生时的客户端 UA (URL 编码)", "Mozilla%2F5.0", "ENVIRONMENT");
        }

        if (fallbackMappings.isEmpty()) {
            // Awin: 花括号小写风格
            seedFallbackMapping("AWIN", "Awin 联盟", "click_id", "{clickid}", "Awin 点击 ID 宏");
            seedFallbackMapping("AWIN", "Awin 联盟", "txid", "{orderid}", "订单号宏");
            seedFallbackMapping("AWIN", "Awin 联盟", "payout", "{earned}", "渠道佣金 (含币种值)");
            seedFallbackMapping("AWIN", "Awin 联盟", "revenue", "{amount}", "订单商品金额");
            seedFallbackMapping("AWIN", "Awin 联盟", "country", "{country}", "收货国家");
            seedFallbackMapping("AWIN", "Awin 联盟", "sub1", "{subid1}", "Awin SubID1");

            // ShareASale: 花括号 s2s 风格
            seedFallbackMapping("SHAREASALE", "ShareASale (Awin US)", "click_id", "{s2sid}", "S2S 事务字符串");
            seedFallbackMapping("SHAREASALE", "ShareASale (Awin US)", "txid", "{orderid}", "订单号");
            seedFallbackMapping("SHAREASALE", "ShareASale (Awin US)", "payout", "{commission}", "应计佣金");
            seedFallbackMapping("SHAREASALE", "ShareASale (Awin US)", "revenue", "{amount}", "订单金额");

            // CJ: 方括号风格
            seedFallbackMapping("CJ", "Commission Junction", "click_id", "[ssn]", "CJ 会话序号 SN");
            seedFallbackMapping("CJ", "Commission Junction", "txid", "[uniqueoid]", "CJ 唯一订单号");
            seedFallbackMapping("CJ", "Commission Junction", "payout", "[reward]", "CJ 应佣金额");
            seedFallbackMapping("CJ", "Commission Junction", "revenue", "[orderamt]", "订单总额");
            seedFallbackMapping("CJ", "Commission Junction", "country", "[shipcountry]", "收货国家");

            // TikTok: 双下划线大写风格
            seedFallbackMapping("TIKTOK", "TikTok Ads", "click_id", "__CLICKID__", "TikTok 点击回传宏");
            seedFallbackMapping("TIKTOK", "TikTok Ads", "sub1", "__CREATIVE__", "创意 ID 透传");
            seedFallbackMapping("TIKTOK", "TikTok Ads", "os", "__OS__", "操作系统");
            seedFallbackMapping("TIKTOK", "TikTok Ads", "ip", "__IP__", "客户端 IP");
            seedFallbackMapping("TIKTOK", "TikTok Ads", "ua", "__UA__", "客户端 UA");

            // AppsFlyer: 花括号风格
            seedFallbackMapping("APPSFLYER", "AppsFlyer MMP", "click_id", "{clickid}", "AF 归因点击 ID");
            seedFallbackMapping("APPSFLYER", "AppsFlyer MMP", "sub1", "{pid}", "渠道媒体来源 pid");
            seedFallbackMapping("APPSFLYER", "AppsFlyer MMP", "country", "{country}", "归因国家");
            seedFallbackMapping("APPSFLYER", "AppsFlyer MMP", "os", "{os}", "设备系统");

            // Google Ads: GCLID 体系
            seedFallbackMapping("GOOGLE", "Google Ads", "click_id", "{gclid}", "Google 点击 ID");
            seedFallbackMapping("GOOGLE", "Google Ads", "txid", "{conversion_external_order_id}", "转化外部订单号");
        }
    }

    private void seedFallbackParam(String key, String name, String desc, String sample, String category) {
        String id = "mcp_fallback_" + key;
        fallbackParams.put(id, new MacroParam(id, key, name, desc, sample, category, "ACTIVE", Instant.now()));
    }

    private void seedFallbackMapping(String code, String platformName, String key, String token, String remark) {
        String id = "pmm_fallback_" + code.toLowerCase() + "_" + key;
        fallbackMappings.put(id, new PlatformMacroMapping(id, code, platformName, key, token, remark, "ACTIVE", Instant.now(), Instant.now()));
    }

    private void initDbSeedParams() {
        seedParam("click_id", "点击唯一标识", "点击追踪会话 ID，归因与防重付的核心主键", "c_8f3a2b1c", "ATTRIBUTION");
        seedParam("sub1", "子渠道标识 1", "渠道自定义流量分层参数 (媒体/版位/创意)", "fb_campaign_01", "SUB_TRACKING");
        seedParam("sub2", "子渠道标识 2", "渠道自定义流量分层参数", "banner_eu", "SUB_TRACKING");
        seedParam("sub3", "子渠道标识 3", "渠道自定义流量分层参数", null, "SUB_TRACKING");
        seedParam("sub4", "子渠道标识 4", "渠道自定义流量分层参数", null, "SUB_TRACKING");
        seedParam("sub5", "子渠道标识 5", "渠道自定义流量分层参数", null, "SUB_TRACKING");
        seedParam("txid", "广告主订单流水号", "S2S 回传订单唯一号 (幂等去重键)", "ORD_20260918_001", "TRANSACTION");
        seedParam("payout", "渠道应佣金额", "回传给渠道的结算佣金", "12.50", "TRANSACTION");
        seedParam("revenue", "订单收入金额", "广告主侧订单金额", "99.00", "TRANSACTION");
        seedParam("country", "访客国家", "点击来源国家 ISO 码", "US", "ENVIRONMENT");
        seedParam("os", "操作系统", "访客设备操作系统", "ios", "ENVIRONMENT");
        seedParam("device_type", "设备类型", "1=Mobile 2=Desktop 3=Tablet", "1", "ENVIRONMENT");
        seedParam("ip", "访客 IP", "点击发生时的客户端 IP", "203.0.113.7", "ENVIRONMENT");
        seedParam("ua", "User-Agent", "点击发生时的客户端 UA (URL 编码)", "Mozilla%2F5.0", "ENVIRONMENT");
    }

    private void initDbSeedMappings() {
        // Awin
        seedMapping("AWIN", "Awin 联盟", "click_id", "{clickid}", "Awin 点击 ID 宏");
        seedMapping("AWIN", "Awin 联盟", "txid", "{orderid}", "订单号宏");
        seedMapping("AWIN", "Awin 联盟", "payout", "{earned}", "渠道佣金 (含币种值)");
        seedMapping("AWIN", "Awin 联盟", "revenue", "{amount}", "订单商品金额");
        seedMapping("AWIN", "Awin 联盟", "country", "{country}", "收货国家");
        seedMapping("AWIN", "Awin 联盟", "sub1", "{subid1}", "Awin SubID1");

        // ShareASale
        seedMapping("SHAREASALE", "ShareASale (Awin US)", "click_id", "{s2sid}", "S2S 事务字符串");
        seedMapping("SHAREASALE", "ShareASale (Awin US)", "txid", "{orderid}", "订单号");
        seedMapping("SHAREASALE", "ShareASale (Awin US)", "payout", "{commission}", "应计佣金");
        seedMapping("SHAREASALE", "ShareASale (Awin US)", "revenue", "{amount}", "订单金额");

        // CJ
        seedMapping("CJ", "Commission Junction", "click_id", "[ssn]", "CJ 会话序号 SN");
        seedMapping("CJ", "Commission Junction", "txid", "[uniqueoid]", "CJ 唯一订单号");
        seedMapping("CJ", "Commission Junction", "payout", "[reward]", "CJ 应佣金额");
        seedMapping("CJ", "Commission Junction", "revenue", "[orderamt]", "订单总额");
        seedMapping("CJ", "Commission Junction", "country", "[shipcountry]", "收货国家");

        // TikTok
        seedMapping("TIKTOK", "TikTok Ads", "click_id", "__CLICKID__", "TikTok 点击回传宏");
        seedMapping("TIKTOK", "TikTok Ads", "sub1", "__CREATIVE__", "创意 ID 透传");
        seedMapping("TIKTOK", "TikTok Ads", "os", "__OS__", "操作系统");
        seedMapping("TIKTOK", "TikTok Ads", "ip", "__IP__", "客户端 IP");
        seedMapping("TIKTOK", "TikTok Ads", "ua", "__UA__", "客户端 UA");

        // AppsFlyer
        seedMapping("APPSFLYER", "AppsFlyer MMP", "click_id", "{clickid}", "AF 归因点击 ID");
        seedMapping("APPSFLYER", "AppsFlyer MMP", "sub1", "{pid}", "渠道媒体来源 pid");
        seedMapping("APPSFLYER", "AppsFlyer MMP", "country", "{country}", "归因国家");
        seedMapping("APPSFLYER", "AppsFlyer MMP", "os", "{os}", "设备系统");

        // Google Ads
        seedMapping("GOOGLE", "Google Ads", "click_id", "{gclid}", "Google 点击 ID");
        seedMapping("GOOGLE", "Google Ads", "txid", "{conversion_external_order_id}", "转化外部订单号");
    }

    private void seedParam(String key, String name, String desc, String sample, String category) {
        if (listParams().stream().anyMatch(p -> p.macroKey().equalsIgnoreCase(key))) return;
        saveParamQuiet(new MacroParam(null, key, name, desc, sample, category, "ACTIVE", null));
    }

    private void seedMapping(String code, String platformName, String key, String token, String remark) {
        if (listMappings(code).stream().anyMatch(m -> m.macroKey().equalsIgnoreCase(key))) return;
        saveMappingQuiet(new PlatformMacroMapping(null, code, platformName, key, token, remark, "ACTIVE", null, null));
    }

    private void saveParamQuiet(MacroParam param) {
        try {
            saveParam(param);
        } catch (Exception ignored) {
            // 种子数据遇并发、已存在或表缺失时静默跳过
        }
    }

    private void saveMappingQuiet(PlatformMacroMapping mapping) {
        try {
            saveMapping(mapping);
        } catch (Exception ignored) {
        }
    }

    private static String normalizeKey(String raw) {
        if (raw == null) return "";
        return raw.trim().replace("{", "").replace("}", "")
                .replace("[", "").replace("]", "").trim().toLowerCase();
    }

    private MacroParam toParamDomain(MacroParamEntity e) {
        return new MacroParam(e.getId(), e.getMacroKey(), e.getDisplayName(), e.getDescription(),
                e.getSampleValue(), e.getCategory(), e.getStatus(), e.getCreatedAt());
    }

    private PlatformMacroMapping toMappingDomain(PlatformMacroMappingEntity e) {
        return new PlatformMacroMapping(e.getId(), e.getPlatformCode(), e.getPlatformName(), e.getMacroKey(),
                e.getPlatformMacroToken(), e.getRemark(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
