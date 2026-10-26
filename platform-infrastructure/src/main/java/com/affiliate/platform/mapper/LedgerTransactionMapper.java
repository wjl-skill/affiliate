package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.LedgerTransactionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * 复式记账事务主表 Mapper (Ledger Transaction Mapper)
 */
@Mapper
public interface LedgerTransactionMapper extends BaseMapper<LedgerTransactionEntity> {

    @Select("SELECT * FROM ledger_transaction WHERE tenant_id = #{tenantId} AND idem_key = #{idemKey} LIMIT 1")
    LedgerTransactionEntity findByIdemKey(@Param("tenantId") String tenantId, @Param("idemKey") String idemKey);

    @Select("SELECT * FROM ledger_transaction WHERE reverses_id = #{reversesId} LIMIT 1")
    LedgerTransactionEntity findByReversesId(@Param("reversesId") String reversesId);
}
