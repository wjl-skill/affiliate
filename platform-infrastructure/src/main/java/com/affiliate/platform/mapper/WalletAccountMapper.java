package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.WalletAccountEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 钱包账户数据访问层 Mapper (Wallet Account Mapper)
 */
@Mapper
public interface WalletAccountMapper extends BaseMapper<WalletAccountEntity> {

    /** 账户充值原子更新 */
    @Update("UPDATE wallet_account SET cash_balance = cash_balance + #{amount}, updated_at = CURRENT_TIMESTAMP WHERE account_id = #{accountId}")
    int atomicRecharge(@Param("accountId") String accountId, @Param("amount") BigDecimal amount);

    /** 竞价前原子预占冻结 (仅当 cash_balance + credit_limit - frozen_amount >= amount 时成功) */
    @Update("UPDATE wallet_account SET frozen_amount = frozen_amount + #{amount}, updated_at = CURRENT_TIMESTAMP WHERE account_id = #{accountId} AND (cash_balance + credit_limit - frozen_amount) >= #{amount}")
    int atomicPreAuthHold(@Param("accountId") String accountId, @Param("amount") BigDecimal amount);

    /** 胜出确认真实扣减 (扣除余额同时释放对应冻结) */
    @Update("UPDATE wallet_account SET cash_balance = cash_balance - #{amount}, frozen_amount = CASE WHEN frozen_amount >= #{amount} THEN frozen_amount - #{amount} ELSE 0 END, updated_at = CURRENT_TIMESTAMP WHERE account_id = #{accountId}")
    int atomicCapture(@Param("accountId") String accountId, @Param("amount") BigDecimal amount);

    /** 释放预占金额 */
    @Update("UPDATE wallet_account SET frozen_amount = CASE WHEN frozen_amount >= #{amount} THEN frozen_amount - #{amount} ELSE 0 END, updated_at = CURRENT_TIMESTAMP WHERE account_id = #{accountId}")
    int atomicRelease(@Param("accountId") String accountId, @Param("amount") BigDecimal amount);

    /** 退款冲正 */
    @Update("UPDATE wallet_account SET cash_balance = cash_balance + #{amount}, updated_at = CURRENT_TIMESTAMP WHERE account_id = #{accountId}")
    int atomicRefund(@Param("accountId") String accountId, @Param("amount") BigDecimal amount);
}
