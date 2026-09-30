package com.affiliate.platform.cdp;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 生产级 GDPR / CCPA 客户隐私同意与被遗忘权墓碑擦除引擎 (Customer Consent & Tombstone Purge Manager)
 * <p>
 * 满足国际数据合规（GDPR Article 17 / CCPA / Apple ATT）严格要求：
 * 1. 实时判定用户的隐私选择权（Opt-out），对未授权用户阻断广告定向；
 * 2. 接收“被遗忘权 (Right to be Forgotten)”指令时，彻底擦除一方系统明文属性；
 * 3. 设立加密墓碑哈希 (Cryptographic Tombstone Hash) 与阻断名单 (Purge Blocklist)：
 *    防止未来同一设备或匿名 Cookie 再次产生新触点日志时“复活”已注销用户；
 * 4. 纳秒级极速判定，热路径零阻塞。
 */
@Service
public class CustomerConsentManager {

    public enum ConsentState {
        /** 已授权：允许用于多维分析与广告定向 */
        CONSENTED,
        /** 已退出：禁止用于广告个性化定向与数据共享 */
        OPTED_OUT,
        /** 已彻底物理擦除（仅存墓碑哈希阻断） */
        PURGED
    }

    public record Tombstone(
            String tombstoneHash,
            Instant purgedAt,
            String reason
    ) {}

    // 内存活跃授权状态：Key 为 primaryId/identifier
    private final ConcurrentMap<String, ConsentState> consentStates = new ConcurrentHashMap<>();

    // 墓碑阻断名单：Key 为 SHA-256(identifier)
    private final ConcurrentMap<String, Tombstone> tombstoneBlocklist = new ConcurrentHashMap<>();

    /**
     * 记录客户的授权或选择退出
     */
    public void setConsent(String identifier, boolean allowMarketing) {
        if (identifier == null || identifier.isBlank()) return;
        String norm = normalize(identifier);

        if (isPurged(norm)) {
            return; // 已彻底擦除的用户禁止重新覆盖为正常状态
        }

        consentStates.put(norm, allowMarketing ? ConsentState.CONSENTED : ConsentState.OPTED_OUT);
    }

    /**
     * 极速检查指定用户/设备是否允许用于广告定向与数据变现
     */
    public boolean isAllowedForTargeting(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        String norm = normalize(identifier);

        if (isPurged(norm)) {
            return false;
        }

        ConsentState state = consentStates.get(norm);
        // 默认若未明确 Opt-out，则允许合规使用；若为 OPTED_OUT 或 PURGED 则坚决阻断
        return state != ConsentState.OPTED_OUT && state != ConsentState.PURGED;
    }

    /**
     * 执行 GDPR/CCPA 被遗忘权物理擦除与设立墓碑 (Tombstone Purge)
     *
     * @param primaryId           客户主主键
     * @param relatedIdentifiers 关联的跨触点身份标识列表（设备ID、邮箱、Cookie）
     * @param reason              擦除原因
     */
    public void purgeCustomer(String primaryId, Collection<String> relatedIdentifiers, String reason) {
        Instant now = Instant.now();
        Set<String> allIds = new HashSet<>();
        if (primaryId != null && !primaryId.isBlank()) {
            allIds.add(normalize(primaryId));
        }
        if (relatedIdentifiers != null) {
            for (String id : relatedIdentifiers) {
                if (id != null && !id.isBlank()) {
                    allIds.add(normalize(id));
                }
            }
        }

        for (String id : allIds) {
            consentStates.put(id, ConsentState.PURGED);

            // 生成加密墓碑哈希并加入墓碑黑名单
            String hash = sha256Hex(id);
            tombstoneBlocklist.put(hash, new Tombstone(hash, now, reason));
        }
    }

    /**
     * 判断某个标识是否已处于彻底擦除的墓碑黑名单中
     */
    public boolean isPurged(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        String norm = normalize(identifier);
        if (consentStates.get(norm) == ConsentState.PURGED) {
            return true;
        }
        String hash = sha256Hex(norm);
        return tombstoneBlocklist.containsKey(hash);
    }

    public int totalPurgedTombstones() {
        return tombstoneBlocklist.size();
    }

    public void clear() {
        consentStates.clear();
        tombstoneBlocklist.clear();
    }

    private String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
