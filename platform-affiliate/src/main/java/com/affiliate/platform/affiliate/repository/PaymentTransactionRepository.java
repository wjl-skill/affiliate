package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.PaymentTransactionEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** MyBatis-Plus payment transaction store. */
@Mapper
public interface PaymentTransactionRepository extends BaseMapper<PaymentTransactionEntity> {
    default PaymentTransactionEntity save(PaymentTransactionEntity entity) {
        if (entity == null) return null;
        if (entity.getId() == null || selectById(entity.getId()) == null) insert(entity);
        else updateById(entity);
        return entity;
    }
    default Optional<PaymentTransactionEntity> findById(String id) { return Optional.ofNullable(selectById(id)); }
    default List<PaymentTransactionEntity> findByAffiliateIdOrderByCreatedAtDesc(String affiliateId) {
        return selectList(new LambdaQueryWrapper<PaymentTransactionEntity>().eq(PaymentTransactionEntity::getAffiliateId, affiliateId)
                .orderByDesc(PaymentTransactionEntity::getCreatedAt));
    }
    default List<PaymentTransactionEntity> findByAffiliateIdAndStatusOrderByCreatedAtDesc(String affiliateId, String status) {
        return selectList(new LambdaQueryWrapper<PaymentTransactionEntity>().eq(PaymentTransactionEntity::getAffiliateId, affiliateId)
                .eq(PaymentTransactionEntity::getStatus, status).orderByDesc(PaymentTransactionEntity::getCreatedAt));
    }
    default List<PaymentTransactionEntity> findByAffiliateAndTimeRange(String affiliateId, Instant from, Instant to) {
        return selectList(new LambdaQueryWrapper<PaymentTransactionEntity>().eq(PaymentTransactionEntity::getAffiliateId, affiliateId)
                .ge(PaymentTransactionEntity::getCreatedAt, from).lt(PaymentTransactionEntity::getCreatedAt, to)
                .orderByDesc(PaymentTransactionEntity::getCreatedAt));
    }
    default List<PaymentTransactionEntity> findByAffiliateStatusAndTimeRange(String affiliateId, String status, Instant from, Instant to) {
        return selectList(new LambdaQueryWrapper<PaymentTransactionEntity>().eq(PaymentTransactionEntity::getAffiliateId, affiliateId)
                .eq(PaymentTransactionEntity::getStatus, status).ge(PaymentTransactionEntity::getCreatedAt, from)
                .lt(PaymentTransactionEntity::getCreatedAt, to).orderByDesc(PaymentTransactionEntity::getCreatedAt));
    }
    default Optional<PaymentTransactionEntity> findByInvoiceId(String invoiceId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<PaymentTransactionEntity>().eq(PaymentTransactionEntity::getInvoiceId, invoiceId).last("LIMIT 1")));
    }
    default Optional<PaymentTransactionEntity> findByExternalPaymentId(String externalPaymentId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<PaymentTransactionEntity>().eq(PaymentTransactionEntity::getExternalPaymentId, externalPaymentId).last("LIMIT 1")));
    }
    default List<PaymentTransactionEntity> findRetryableTransactions(int maxRetries) {
        return selectList(new LambdaQueryWrapper<PaymentTransactionEntity>().eq(PaymentTransactionEntity::getStatus, "FAILED")
                .lt(PaymentTransactionEntity::getRetryCount, maxRetries).orderByDesc(PaymentTransactionEntity::getCreatedAt));
    }
    default List<PaymentTransactionEntity> findByStatusOrderByCreatedAtAsc(String status) {
        return selectList(new LambdaQueryWrapper<PaymentTransactionEntity>().eq(PaymentTransactionEntity::getStatus, status)
                .orderByAsc(PaymentTransactionEntity::getCreatedAt));
    }
    default BigDecimal sumCompletedPaymentsByAffiliate(String affiliateId) {
        return sum(selectList(new LambdaQueryWrapper<PaymentTransactionEntity>().eq(PaymentTransactionEntity::getAffiliateId, affiliateId)
                .eq(PaymentTransactionEntity::getStatus, "COMPLETED")));
    }
    default BigDecimal sumCompletedPaymentsByTimeRange(Instant from, Instant to) {
        return sum(selectList(new LambdaQueryWrapper<PaymentTransactionEntity>().eq(PaymentTransactionEntity::getStatus, "COMPLETED")
                .ge(PaymentTransactionEntity::getCreatedAt, from).lt(PaymentTransactionEntity::getCreatedAt, to)));
    }
    default List<Object[]> countByStatus() {
        return selectList(null).stream().collect(Collectors.groupingBy(PaymentTransactionEntity::getStatus, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new Object[]{e.getKey(), e.getValue()}).toList();
    }
    default List<Object[]> statsByPaymentMethod() {
        Map<String, List<PaymentTransactionEntity>> grouped = selectList(new LambdaQueryWrapper<PaymentTransactionEntity>().eq(PaymentTransactionEntity::getStatus, "COMPLETED"))
                .stream().collect(Collectors.groupingBy(PaymentTransactionEntity::getPaymentMethodId, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream().map(e -> new Object[]{e.getKey(), (long)e.getValue().size(), sum(e.getValue())}).toList();
    }
    private static BigDecimal sum(List<PaymentTransactionEntity> entities) {
        return entities.stream().map(PaymentTransactionEntity::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
