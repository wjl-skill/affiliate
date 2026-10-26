package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.LedgerLineEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 复式借贷分录明细 Mapper (Ledger Line Mapper)
 */
@Mapper
public interface LedgerLineMapper extends BaseMapper<LedgerLineEntity> {

    /**
     * 聚合计算目标账户基于全部不可变会计分录的历史净累计额 (Credit 贷方增加 - Debit 借方扣减)
     * 用于与 wallet_account 的物化余额 cash_balance 进行日终对账比对
     */
    @Select("SELECT COALESCE(SUM(CASE WHEN direction = 'C' THEN amount ELSE -amount END), 0) " +
            "FROM ledger_line " +
            "WHERE tenant_id = #{tenantId} AND account_id = #{accountId}")
    BigDecimal calculateNetBalance(@Param("tenantId") String tenantId, @Param("accountId") String accountId);

    /**
     * 校验指定事务 ID 下的所有分录是否严格借贷平衡 (SUM(D) == SUM(C))
     * 返回 0 代表完全平衡
     */
    @Select("SELECT COALESCE(SUM(CASE WHEN direction = 'D' THEN amount ELSE -amount END), 0) " +
            "FROM ledger_line WHERE txn_id = #{txnId}")
    BigDecimal checkTxnBalance(@Param("txnId") String txnId);

    /**
     * 查询指定事务的所有分录明细
     */
    @Select("SELECT * FROM ledger_line WHERE txn_id = #{txnId} ORDER BY id ASC")
    List<LedgerLineEntity> listByTxnId(@Param("txnId") String txnId);
}
