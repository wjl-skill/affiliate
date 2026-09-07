package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.entity.AffiliateInvoiceEntity;
import com.affiliate.platform.mapper.AffiliateInvoiceMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 联盟营销财务审核与账期结算服务 (Affiliate Settlement & Invoicing Service - MyBatis-Plus)
 * <p>
 * 基于 MyBatis-Plus 接入 PostgreSQL `affiliate_invoice` 表。
 */
@Service
public class AffiliateSettlementService {

    private final S2sPostbackService postbackService;
    private final AffiliateInvoiceMapper invoiceMapper;
    private final ConcurrentMap<String, AffiliateInvoice> fallbackInvoices = new ConcurrentHashMap<>();

    public AffiliateSettlementService(S2sPostbackService postbackService) {
        this(postbackService, null);
    }

    @Autowired
    public AffiliateSettlementService(
            S2sPostbackService postbackService,
            @Autowired(required = false) AffiliateInvoiceMapper invoiceMapper
    ) {
        this.postbackService = postbackService;
        this.invoiceMapper = invoiceMapper;
    }

    public Conversion approve(String conversionId) {
        Conversion conv = postbackService.findConversion(conversionId)
                .orElseThrow(() -> new IllegalArgumentException("conversion not found: " + conversionId));
        Conversion approved = conv.approve();
        postbackService.updateConversion(approved);
        return approved;
    }

    public Conversion reject(String conversionId, String reason) {
        Conversion conv = postbackService.findConversion(conversionId)
                .orElseThrow(() -> new IllegalArgumentException("conversion not found: " + conversionId));
        Conversion rejected = conv.reject(reason);
        postbackService.updateConversion(rejected);
        return rejected;
    }

    public Optional<AffiliateInvoice> generateInvoice(String affiliateId, AffiliatePartner partner) {
        if (partner == null) return Optional.empty();

        List<Conversion> approvedList = postbackService.listConversions().stream()
                .filter(c -> c.affiliateId().equalsIgnoreCase(affiliateId) && c.status() == Conversion.Status.APPROVED)
                .toList();

        if (approvedList.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal totalPayout = approvedList.stream()
                .map(Conversion::payout)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPayout.compareTo(partner.minPayoutThreshold()) < 0) {
            return Optional.empty();
        }

        String invoiceId = "inv_" + UUID.randomUUID().toString().replace("-", "");
        AffiliateInvoice invoice = new AffiliateInvoice(
                invoiceId,
                partner.tenantId(),
                affiliateId,
                totalPayout,
                approvedList.size(),
                partner.paymentTerm(),
                InvoiceStatus.GENERATED,
                Instant.now()
        );

        if (invoiceMapper != null) {
            AffiliateInvoiceEntity entity = new AffiliateInvoiceEntity(
                    invoiceId,
                    partner.tenantId(),
                    affiliateId,
                    "CYCLE_" + Instant.now().toString().substring(0, 7),
                    totalPayout,
                    InvoiceStatus.GENERATED.name(),
                    partner.paymentTerm().name(),
                    Instant.now(),
                    null
            );
            invoiceMapper.insert(entity);
            return Optional.of(invoice);
        }

        fallbackInvoices.put(invoiceId, invoice);
        return Optional.of(invoice);
    }

    public List<AffiliateInvoice> listInvoices() {
        if (invoiceMapper != null) {
            QueryWrapper<AffiliateInvoiceEntity> qw = new QueryWrapper<>();
            qw.orderByDesc("created_at").last("LIMIT 1000");
            List<AffiliateInvoiceEntity> list = invoiceMapper.selectList(qw);
            return list.stream().map(e -> new AffiliateInvoice(
                    e.getId(),
                    e.getTenantId(),
                    e.getAffiliateId(),
                    e.getAmount(),
                    1,
                    AffiliatePartner.PaymentTerm.valueOf(e.getPaymentTerm()),
                    InvoiceStatus.valueOf(e.getStatus()),
                    e.getCreatedAt()
            )).toList();
        }
        return List.copyOf(fallbackInvoices.values());
    }

    public record AffiliateInvoice(
            String id,
            String tenantId,
            String affiliateId,
            BigDecimal amount,
            int conversionCount,
            AffiliatePartner.PaymentTerm paymentTerm,
            InvoiceStatus status,
            Instant createdAt
    ) {}

    public enum InvoiceStatus {
        GENERATED,
        PAID
    }
}
