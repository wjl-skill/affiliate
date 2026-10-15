package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定 MyBatis-Plus 的列元数据，避免实体属性被错误转换为不存在的数据库列。
 */
class EntitySchemaMappingTest {

    @Test
    void conversionUsesAffiliateConversionColumns() {
        TableInfo tableInfo = tableInfo(ConversionEntity.class);
        Set<String> columns = tableInfo.getFieldList().stream()
                .map(TableFieldInfo::getColumn)
                .collect(Collectors.toSet());

        assertEquals("id", tableInfo.getKeyColumn());
        assertTrue(columns.containsAll(Set.of(
                "tenant_id", "click_id", "transaction_id", "offer_id", "affiliate_id",
                "payout", "revenue", "sale_amount", "ctit_seconds", "status",
                "rejection_reason", "sub1", "postback_status", "created_at")));
        String selectList = tableInfo.getAllSqlSelect();
        assertTrue(selectList.contains("transaction_id"));
        assertTrue(selectList.contains("sale_amount"));
        assertTrue(selectList.contains("rejection_reason"));
        assertTrue(selectList.contains("sub1"));
        assertTrue(selectList.contains("postback_status"));
    }

    @Test
    void offerPersistsTargetingArrayColumns() {
        TableInfo tableInfo = tableInfo(OfferEntity.class);
        Set<String> columns = tableInfo.getFieldList().stream()
                .map(TableFieldInfo::getColumn)
                .collect(Collectors.toSet());
        assertTrue(columns.contains("allowed_countries"));
        assertTrue(columns.contains("allowed_devices"));
    }

    private TableInfo tableInfo(Class<?> entityType) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "entity-schema-test");
        assistant.setCurrentNamespace(entityType.getName());
        return TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
