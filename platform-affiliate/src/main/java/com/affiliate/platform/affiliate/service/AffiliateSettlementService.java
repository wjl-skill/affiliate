package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.entity.AffiliateInvoiceEntity;
import com.affiliate.platform.mapper.AffiliateInvoiceMapper;
import com.affiliate.platform.tenant.TenantContext;
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
                .filter(c -> c.tenantId() == null || c.tenantId().equalsIgnoreCase(partner.tenantId()))
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
        String billingCycle = "CYCLE_" + Instant.now().toString().substring(0, 7);
        Instant issuedAt = Instant.now();
        AffiliateInvoice invoice = new AffiliateInvoice(
                invoiceId,
                partner.tenantId(),
                affiliateId,
                billingCycle,
                totalPayout,
                approvedList.size(),
                partner.paymentTerm(),
                InvoiceStatus.GENERATED,
                issuedAt,
                null
        );

        if (invoiceMapper != null) {
            AffiliateInvoiceEntity entity = new AffiliateInvoiceEntity(
                    invoiceId,
                    partner.tenantId(),
                    affiliateId,
                    billingCycle,
                    totalPayout,
                    approvedList.size(),
                    InvoiceStatus.GENERATED.name(),
                    partner.paymentTerm().name(),
                    issuedAt,
                    null
            );
            invoiceMapper.insert(entity);
            return Optional.of(invoice);
        }

        fallbackInvoices.put(invoiceId, invoice);
        return Optional.of(invoice);
    }

    /** 当前请求线程绑定的租户；非请求线程（离线测试）为空时不做租户收窄 */
    private static Optional<String> requestTenant() {
        String tenant = TenantContext.get();
        return tenant != null && !tenant.isBlank() ? Optional.of(tenant) : Optional.empty();
    }

    public List<AffiliateInvoice> listInvoices() {
        if (invoiceMapper != null) {
            QueryWrapper<AffiliateInvoiceEntity> qw = new QueryWrapper<>();
            requestTenant().ifPresent(t -> qw.eq("tenant_id", t));
            qw.orderByDesc("created_at").last("LIMIT 1000");
            List<AffiliateInvoiceEntity> list = invoiceMapper.selectList(qw);
            return list.stream().map(this::toDomain).toList();
        }
        String tenant = requestTenant().orElse(null);
        return fallbackInvoices.values().stream()
                .filter(inv -> tenant == null || tenant.equals(inv.tenantId()))
                .toList();
    }

    /**
     * 将结算发票标记为已支付 (GENERATED -> PAID)，仅限当前租户
     *
     * @param invoiceId 发票 ID
     * @return 更新后的发票，未找到或跨租户时为 empty
     */
    public Optional<AffiliateInvoice> markInvoicePaid(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) return Optional.empty();

        if (invoiceMapper != null) {
            AffiliateInvoiceEntity entity = invoiceMapper.selectById(invoiceId);
            if (entity == null) return Optional.empty();
            String tenant = requestTenant().orElse(null);
            if (tenant != null && !tenant.equals(entity.getTenantId())) {
                return Optional.empty();
            }
            entity.setStatus(InvoiceStatus.PAID.name());
            entity.setPaidAt(Instant.now());
            invoiceMapper.updateById(entity);
            return Optional.of(toDomain(entity));
        }

        AffiliateInvoice invoice = fallbackInvoices.get(invoiceId);
        if (invoice == null) return Optional.empty();
        String tenant = requestTenant().orElse(null);
        if (tenant != null && !tenant.equals(invoice.tenantId())) {
            return Optional.empty();
        }
        AffiliateInvoice paid = new AffiliateInvoice(
                invoice.id(), invoice.tenantId(), invoice.affiliateId(), invoice.billingCycle(),
                invoice.amount(), invoice.conversionCount(), invoice.paymentTerm(),
                InvoiceStatus.PAID, invoice.createdAt(), Instant.now()
        );
        fallbackInvoices.put(invoiceId, paid);
        return Optional.of(paid);
    }

    private AffiliateInvoice toDomain(AffiliateInvoiceEntity e) {
        return new AffiliateInvoice(
                e.getId(),
                e.getTenantId(),
                e.getAffiliateId(),
                e.getBillingCycle(),
                e.getAmount(),
                e.getConversionCount() == null ? 0 : e.getConversionCount(),
                AffiliatePartner.PaymentTerm.valueOf(e.getPaymentTerm()),
                InvoiceStatus.valueOf(e.getStatus()),
                e.getCreatedAt(),
                e.getPaidAt()
        );
    }

    public record AffiliateInvoice(
            String id,
            String tenantId,
            String affiliateId,
            String billingCycle,
            BigDecimal amount,
            int conversionCount,
            AffiliatePartner.PaymentTerm paymentTerm,
            InvoiceStatus status,
            Instant createdAt,
            Instant paidAt
    ) {}

    public enum InvoiceStatus {
        GENERATED,
        PAID
    }
}
