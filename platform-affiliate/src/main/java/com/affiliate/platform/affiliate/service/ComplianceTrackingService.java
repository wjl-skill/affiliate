package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.cache.CacheKeyGenerator;
import com.affiliate.platform.affiliate.cache.MultiLevelCacheManager;
import com.affiliate.platform.affiliate.domain.*;
import com.affiliate.platform.affiliate.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 合规与文档管理服务（重构版 - 接入 PostgreSQL + 多级缓存）
 * <p>
 * 功能：
 * 1. 服务条款接受追踪
 * 2. 税务表单管理（W-9、W-8BEN、1099-MISC）
 * 3. 身份验证文档（KYC）
 * 4. 支付方式验证
 * 5. 营销合规检查（广告法、隐私政策）
 * 6. 数据保留与 GDPR 合规
 * <p>
 * 对标：Impact Compliance Center、CJ Affiliate Compliance Dashboard
 */
@Service
public class ComplianceTrackingService {

    private final TermsAcceptanceRepository termsRepository;
    private final TaxDocumentRepository taxDocumentRepository;
    private final KycVerificationRepository kycRepository;
    private final ComplianceViolationRepository violationRepository;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;

    private static final Duration KYC_CACHE_TTL = Duration.ofHours(1);
    private static final Duration TAX_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration TERMS_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration VIOLATION_CACHE_TTL = Duration.ofMinutes(10);

    // 当前服务条款版本
    private static final String CURRENT_TERMS_VERSION = "2.1";
    private static final LocalDate CURRENT_TERMS_EFFECTIVE_DATE = LocalDate.of(2026, 1, 1);

    public ComplianceTrackingService(
            TermsAcceptanceRepository termsRepository,
            TaxDocumentRepository taxDocumentRepository,
            KycVerificationRepository kycRepository,
            ComplianceViolationRepository violationRepository,
            MultiLevelCacheManager cacheManager,
            CacheKeyGenerator keyGenerator
    ) {
        this.termsRepository = termsRepository;
        this.taxDocumentRepository = taxDocumentRepository;
        this.kycRepository = kycRepository;
        this.violationRepository = violationRepository;
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
    }

    /**
     * 记录服务条款接受
     */
    @Transactional
    public TermsAcceptance acceptTerms(
            String affiliateId,
            String version,
            String ipAddress,
            String userAgent
    ) {
        TermsAcceptanceEntity entity = new TermsAcceptanceEntity(
                UUID.randomUUID().toString(),
                affiliateId,
                version,
                ipAddress,
                userAgent,
                Instant.now()
        );

        termsRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.termsAcceptance(affiliateId));
        cacheManager.evictByPattern("affiliate:compliance:report:" + affiliateId);

        return toTermsAcceptance(entity);
    }

    /**
     * 检查是否接受最新条款（带缓存）
     */
    public boolean hasAcceptedLatestTerms(String affiliateId) {
        String cacheKey = keyGenerator.termsAcceptance(affiliateId);

        return cacheManager.get(
                cacheKey,
                Boolean.class,
                TERMS_CACHE_TTL,
                () -> termsRepository.existsByAffiliateIdAndVersion(affiliateId, CURRENT_TERMS_VERSION)
        ).orElse(false);
    }

    /**
     * 获取需要重新接受条款的渠道列表
     */
    public List<String> getAffiliatesRequiringTermsUpdate() {
        return termsRepository.findAffiliatesNotAcceptedVersion(CURRENT_TERMS_VERSION);
    }

    /**
     * 上传税务文档
     */
    @Transactional
    public TaxDocument uploadTaxDocument(
            String affiliateId,
            TaxFormType formType,
            String documentUrl,
            String taxId,
            String legalName,
            String country
    ) {
        // 验证表单类型与国家匹配
        validateTaxFormForCountry(formType, country);

        String documentId = UUID.randomUUID().toString();
        TaxDocumentEntity entity = new TaxDocumentEntity(
                documentId,
                affiliateId,
                formType.name(),
                documentUrl,
                taxId,
                legalName,
                country,
                TaxDocumentStatus.PENDING_REVIEW.name(),
                null,
                Instant.now(),
                null,
                getNextYearExpiry()
        );

        taxDocumentRepository.save(entity);

        // 失效缓存
        cacheManager.evictByPattern("affiliate:tax_document:" + affiliateId + ":*");
        cacheManager.evictByPattern("affiliate:compliance:report:" + affiliateId);

        return toTaxDocument(entity);
    }

    /**
     * 审核税务文档
     */
    @Transactional
    public TaxDocument reviewTaxDocument(
            String documentId,
            boolean approved,
            String reviewerNote
    ) {
        TaxDocumentEntity entity = taxDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Tax document not found"));

        entity.setStatus(approved ? TaxDocumentStatus.APPROVED.name() : TaxDocumentStatus.REJECTED.name());
        entity.setReviewerNote(reviewerNote);
        entity.setReviewedAt(Instant.now());
        taxDocumentRepository.save(entity);

        // 失效缓存
        cacheManager.evictByPattern("affiliate:tax_document:" + entity.getAffiliateId() + ":*");
        cacheManager.evictByPattern("affiliate:compliance:report:" + entity.getAffiliateId());

        return toTaxDocument(entity);
    }

    /**
     * 检查税务文档是否完备（带缓存）
     */
    public TaxComplianceStatus getTaxComplianceStatus(String affiliateId, String country) {
        String cacheKey = keyGenerator.taxCompliance(affiliateId, country);

        return cacheManager.get(
                cacheKey,
                TaxComplianceStatus.class,
                TAX_CACHE_TTL,
                () -> calculateTaxComplianceStatus(affiliateId, country)
        ).orElseGet(() -> calculateTaxComplianceStatus(affiliateId, country));
    }

    /**
     * 计算税务合规状态
     */
    private TaxComplianceStatus calculateTaxComplianceStatus(String affiliateId, String country) {
        List<TaxDocumentEntity> validDocs = taxDocumentRepository.findValidTaxDocuments(
                affiliateId,
                Instant.now()
        );

        TaxDocumentEntity validDoc = validDocs.stream()
                .filter(d -> d.getCountry().equals(country))
                .findFirst()
                .orElse(null);

        if (validDoc == null) {
            TaxFormType requiredForm = getRequiredTaxForm(country);
            return new TaxComplianceStatus(
                    affiliateId,
                    false,
                    requiredForm,
                    null,
                    "No valid tax document on file"
            );
        }

        return new TaxComplianceStatus(
                affiliateId,
                true,
                TaxFormType.valueOf(validDoc.getFormType()),
                validDoc.getExpiresAt(),
                "Compliant"
        );
    }

    /**
     * 启动 KYC 身份验证
     */
    @Transactional
    public KycVerification initiateKyc(
            String affiliateId,
            String fullName,
            String dateOfBirth,
            String address,
            String idDocumentUrl
    ) {
        String verificationId = UUID.randomUUID().toString();

        KycVerificationEntity entity = new KycVerificationEntity(
                verificationId,
                affiliateId,
                fullName,
                dateOfBirth,
                address,
                idDocumentUrl,
                KycStatus.PENDING.name(),
                null,
                0,
                Instant.now(),
                null
        );

        kycRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.kycVerification(affiliateId));
        cacheManager.evictByPattern("affiliate:compliance:report:" + affiliateId);

        // TODO: 调用第三方 KYC 服务（Jumio、Onfido、Stripe Identity）

        return toKycVerification(entity);
    }

    /**
     * 更新 KYC 状态
     */
    @Transactional
    public KycVerification updateKycStatus(
            String affiliateId,
            KycStatus newStatus,
            String rejectionReason,
            int riskScore
    ) {
        KycVerificationEntity entity = kycRepository.findByAffiliateId(affiliateId)
                .orElseThrow(() -> new IllegalArgumentException("KYC verification not found"));

        entity.setStatus(newStatus.name());
        entity.setRejectionReason(rejectionReason);
        entity.setRiskScore(riskScore);
        entity.setCompletedAt(Instant.now());
        kycRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.kycVerification(affiliateId));
        cacheManager.evictByPattern("affiliate:compliance:report:" + affiliateId);

        return toKycVerification(entity);
    }

    /**
     * 检查 KYC 是否通过（带缓存）
     */
    public boolean isKycVerified(String affiliateId) {
        String cacheKey = keyGenerator.kycVerification(affiliateId);

        return cacheManager.get(
                cacheKey,
                KycVerificationEntity.class,
                KYC_CACHE_TTL,
                () -> kycRepository.findByAffiliateId(affiliateId).orElse(null)
        ).map(entity -> KycStatus.VERIFIED.name().equals(entity.getStatus())).orElse(false);
    }

    /**
     * 记录合规违规
     */
    @Transactional
    public ComplianceViolation recordViolation(
            String affiliateId,
            ViolationType type,
            String description,
            ViolationSeverity severity,
            String evidence
    ) {
        String violationId = UUID.randomUUID().toString();

        ComplianceViolationEntity entity = new ComplianceViolationEntity(
                violationId,
                affiliateId,
                type.name(),
                description,
                severity.name(),
                evidence,
                ViolationStatus.OPEN.name(),
                null,
                Instant.now(),
                null
        );

        violationRepository.save(entity);

        // 失效缓存
        cacheManager.evictByPattern("affiliate:violation:" + affiliateId + ":*");
        cacheManager.evictByPattern("affiliate:compliance:report:" + affiliateId);

        // 严重违规自动警告
        if (severity == ViolationSeverity.CRITICAL) {
            System.out.println("[ALERT] Critical violation for affiliate: " + affiliateId);
            // TODO: 调用 AffiliatePartnerService 暂停账户
        }

        return toComplianceViolation(entity);
    }

    /**
     * 处理违规（警告、罚款、暂停、封禁）
     */
    @Transactional
    public ComplianceViolation resolveViolation(
            String violationId,
            String resolution,
            ViolationStatus newStatus
    ) {
        ComplianceViolationEntity entity = violationRepository.findById(violationId)
                .orElseThrow(() -> new IllegalArgumentException("Violation not found"));

        entity.setStatus(newStatus.name());
        entity.setResolution(resolution);
        entity.setResolvedAt(Instant.now());
        violationRepository.save(entity);

        // 失效缓存
        cacheManager.evictByPattern("affiliate:violation:" + entity.getAffiliateId() + ":*");
        cacheManager.evictByPattern("affiliate:compliance:report:" + entity.getAffiliateId());

        return toComplianceViolation(entity);
    }

    /**
     * 获取渠道的违规历史
     */
    public List<ComplianceViolation> getViolationHistory(
            String affiliateId,
            ViolationStatus status
    ) {
        List<ComplianceViolationEntity> entities;

        if (status != null) {
            entities = violationRepository.findByAffiliateIdAndStatusOrderByDetectedAtDesc(
                    affiliateId,
                    status.name()
            );
        } else {
            entities = violationRepository.findByAffiliateIdOrderByDetectedAtDesc(affiliateId);
        }

        return entities.stream()
                .map(this::toComplianceViolation)
                .collect(Collectors.toList());
    }

    /**
     * 计算合规风险评分（带缓存）
     */
    public ComplianceRiskScore calculateRiskScore(String affiliateId) {
        String cacheKey = "affiliate:compliance:risk_score:" + affiliateId;

        return cacheManager.get(
                cacheKey,
                ComplianceRiskScore.class,
                Duration.ofMinutes(15),
                () -> performRiskCalculation(affiliateId)
        ).orElseGet(() -> performRiskCalculation(affiliateId));
    }

    /**
     * 执行风险评分计算
     */
    private ComplianceRiskScore performRiskCalculation(String affiliateId) {
        int score = 100; // 满分 100

        // 未接受最新条款：-10 分
        if (!hasAcceptedLatestTerms(affiliateId)) {
            score -= 10;
        }

        // 未完成 KYC：-15 分
        if (!isKycVerified(affiliateId)) {
            score -= 15;
        }

        // 税务文档缺失或过期：-20 分
        TaxComplianceStatus taxStatus = getTaxComplianceStatus(affiliateId, "US");
        if (!taxStatus.compliant()) {
            score -= 20;
        }

        // 违规记录扣分
        List<ComplianceViolation> openViolations = getViolationHistory(affiliateId, ViolationStatus.OPEN);
        for (ComplianceViolation violation : openViolations) {
            score -= switch (violation.severity()) {
                case LOW -> 5;
                case MEDIUM -> 10;
                case HIGH -> 20;
                case CRITICAL -> 40;
            };
        }

        score = Math.max(0, score); // 最低 0 分

        RiskLevel riskLevel = getRiskLevel(score);

        return new ComplianceRiskScore(
                affiliateId,
                score,
                riskLevel,
                openViolations.size(),
                Instant.now()
        );
    }

    /**
     * 生成合规报告（带缓存）
     */
    public ComplianceReport generateComplianceReport(String affiliateId) {
        String cacheKey = "affiliate:compliance:report:" + affiliateId;

        return cacheManager.get(
                cacheKey,
                ComplianceReport.class,
                Duration.ofMinutes(15),
                () -> buildComplianceReport(affiliateId)
        ).orElseGet(() -> buildComplianceReport(affiliateId));
    }

    /**
     * 构建合规报告
     */
    private ComplianceReport buildComplianceReport(String affiliateId) {
        return new ComplianceReport(
                affiliateId,
                hasAcceptedLatestTerms(affiliateId),
                isKycVerified(affiliateId),
                getTaxComplianceStatus(affiliateId, "US"),
                getViolationHistory(affiliateId, ViolationStatus.OPEN),
                calculateRiskScore(affiliateId),
                Instant.now()
        );
    }

    /**
     * 查找即将过期的税务文档
     */
    public List<TaxDocument> getExpiringTaxDocuments(int daysThreshold) {
        Instant now = Instant.now();
        Instant threshold = now.plus(Duration.ofDays(daysThreshold));

        List<TaxDocumentEntity> entities = taxDocumentRepository.findExpiringDocuments(now, threshold);

        return entities.stream()
                .map(this::toTaxDocument)
                .collect(Collectors.toList());
    }

    // ========== 私有辅助方法 ==========

    private void validateTaxFormForCountry(TaxFormType formType, String country) {
        if (country.equals("US")) {
            if (formType != TaxFormType.W9 && formType != TaxFormType.W8BEN) {
                throw new IllegalArgumentException("Invalid tax form for US affiliate");
            }
        } else {
            if (formType == TaxFormType.W9) {
                throw new IllegalArgumentException("W-9 is only for US residents");
            }
        }
    }

    private TaxFormType getRequiredTaxForm(String country) {
        return country.equals("US") ? TaxFormType.W9 : TaxFormType.W8BEN;
    }

    private Instant getNextYearExpiry() {
        return LocalDate.now().plusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private RiskLevel getRiskLevel(int score) {
        if (score >= 80) return RiskLevel.LOW;
        if (score >= 60) return RiskLevel.MEDIUM;
        if (score >= 40) return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }

    private TermsAcceptance toTermsAcceptance(TermsAcceptanceEntity entity) {
        return new TermsAcceptance(
                entity.getId(),
                entity.getAffiliateId(),
                entity.getVersion(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getAcceptedAt()
        );
    }

    private TaxDocument toTaxDocument(TaxDocumentEntity entity) {
        return new TaxDocument(
                entity.getId(),
                entity.getAffiliateId(),
                TaxFormType.valueOf(entity.getFormType()),
                entity.getDocumentUrl(),
                entity.getTaxId(),
                entity.getLegalName(),
                entity.getCountry(),
                TaxDocumentStatus.valueOf(entity.getStatus()),
                entity.getReviewerNote(),
                entity.getUploadedAt(),
                entity.getReviewedAt(),
                entity.getExpiresAt()
        );
    }

    private KycVerification toKycVerification(KycVerificationEntity entity) {
        return new KycVerification(
                entity.getId(),
                entity.getAffiliateId(),
                entity.getFullName(),
                entity.getDateOfBirth(),
                entity.getAddress(),
                entity.getIdDocumentUrl(),
                KycStatus.valueOf(entity.getStatus()),
                entity.getRejectionReason(),
                entity.getRiskScore(),
                entity.getInitiatedAt(),
                entity.getCompletedAt()
        );
    }

    private ComplianceViolation toComplianceViolation(ComplianceViolationEntity entity) {
        return new ComplianceViolation(
                entity.getId(),
                entity.getAffiliateId(),
                ViolationType.valueOf(entity.getType()),
                entity.getDescription(),
                ViolationSeverity.valueOf(entity.getSeverity()),
                entity.getEvidence(),
                ViolationStatus.valueOf(entity.getStatus()),
                entity.getResolution(),
                entity.getDetectedAt(),
                entity.getResolvedAt()
        );
    }

    // ========== 数据记录 ==========

    public record TermsAcceptance(
            String id,
            String affiliateId,
            String version,
            String ipAddress,
            String userAgent,
            Instant acceptedAt
    ) {}

    public record TaxDocument(
            String id,
            String affiliateId,
            TaxFormType formType,
            String documentUrl,
            String taxId,
            String legalName,
            String country,
            TaxDocumentStatus status,
            String reviewerNote,
            Instant uploadedAt,
            Instant reviewedAt,
            Instant expiresAt
    ) {}

    public record TaxComplianceStatus(
            String affiliateId,
            boolean compliant,
            TaxFormType requiredForm,
            Instant validUntil,
            String message
    ) {}

    public record KycVerification(
            String id,
            String affiliateId,
            String fullName,
            String dateOfBirth,
            String address,
            String idDocumentUrl,
            KycStatus status,
            String rejectionReason,
            int riskScore,
            Instant initiatedAt,
            Instant completedAt
    ) {}

    public record ComplianceViolation(
            String id,
            String affiliateId,
            ViolationType type,
            String description,
            ViolationSeverity severity,
            String evidence,
            ViolationStatus status,
            String resolution,
            Instant detectedAt,
            Instant resolvedAt
    ) {}

    public record ComplianceRiskScore(
            String affiliateId,
            int score,
            RiskLevel riskLevel,
            int openViolations,
            Instant calculatedAt
    ) {}

    public record ComplianceReport(
            String affiliateId,
            boolean termsAccepted,
            boolean kycVerified,
            TaxComplianceStatus taxStatus,
            List<ComplianceViolation> openViolations,
            ComplianceRiskScore riskScore,
            Instant generatedAt
    ) {}

    public enum TaxFormType {
        W9,              // US 居民
        W8BEN,           // 非美国个人
        W8BEN_E,         // 非美国实体
        FORM_1099_MISC   // IRS 报税表
    }

    public enum TaxDocumentStatus {
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        EXPIRED
    }

    public enum KycStatus {
        PENDING,
        VERIFIED,
        REJECTED,
        EXPIRED
    }

    public enum ViolationType {
        FRAUD,                  // 欺诈
        TRADEMARK_VIOLATION,    // 商标侵权
        COOKIE_STUFFING,        // Cookie 填充
        CLICK_INJECTION,        // 点击注入
        SPAM,                   // 垃圾邮件
        MISLEADING_ADS,         // 误导性广告
        BRAND_BIDDING,          // 品牌词竞价
        INCENTIVIZED_TRAFFIC,   // 激励流量
        UNDISCLOSED_AFFILIATE   // 未披露联盟关系
    }

    public enum ViolationSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum ViolationStatus {
        OPEN,
        WARNING_ISSUED,
        UNDER_INVESTIGATION,
        RESOLVED,
        ACCOUNT_SUSPENDED,
        ACCOUNT_TERMINATED
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
