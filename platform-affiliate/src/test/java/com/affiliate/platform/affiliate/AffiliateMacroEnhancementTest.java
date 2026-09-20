package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.MacroParam;
import com.affiliate.platform.affiliate.domain.PlatformMacroMapping;
import com.affiliate.platform.affiliate.service.AffiliateMacroService;
import com.affiliate.platform.entity.MacroParamEntity;
import com.affiliate.platform.entity.PlatformMacroMappingEntity;
import com.affiliate.platform.mapper.MacroParamMapper;
import com.affiliate.platform.mapper.PlatformMacroMappingMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AffiliateMacroEnhancementTest {

    @Test
    @DisplayName("(a) 种子保护测试：当缺V12宏表抛出SQL异常时，Service仍平稳初始化且降级内存种子")
    void testSeedProtectionWhenV12TableMissing() {
        MacroParamMapper mockParamMapper = mock(MacroParamMapper.class);
        PlatformMacroMappingMapper mockMappingMapper = mock(PlatformMacroMappingMapper.class);

        // 模拟数据库尚无 V12 表，执行 selectCount 抛出 BadSqlGrammarException (relation does not exist)
        when(mockParamMapper.selectCount(any())).thenThrow(
                new BadSqlGrammarException("selectCount", "SELECT COUNT(*) FROM affiliate_macro_param",
                        new SQLException("Relation 'affiliate_macro_param' does not exist"))
        );

        // 验证构造函数执行绝不抛出任何未捕获异常，确保 Spring 容器正常启动
        AffiliateMacroService service = assertDoesNotThrow(() ->
                new AffiliateMacroService(mockParamMapper, mockMappingMapper)
        );

        // 验证降级到内存种子字典，依然能查询到完整的标准宏列表（如 click_id, sub1 等）
        List<MacroParam> params = service.listParams();
        assertNotNull(params);
        assertFalse(params.isEmpty(), "即使缺 V12 表，内存 fallback 也应提供预置标准宏种子");
        assertTrue(params.stream().anyMatch(p -> "click_id".equalsIgnoreCase(p.macroKey())));
    }

    @Test
    @DisplayName("(b) 按 token 长度倒序替换避免子串误伤测试")
    void testReverseLengthOrderingPreventsSubstringCorruption() {
        AffiliateMacroService service = new AffiliateMacroService();

        // 注册两个互为子串的平台宏映射：短 token "[subid]" 与长 token "[subid_extra]"
        service.saveMapping(new PlatformMacroMapping(
                null, "TEST_PLAT", "Test Platform", "sub1", "[subid]",
                "短token", "ACTIVE", Instant.now(), Instant.now()
        ));
        service.saveMapping(new PlatformMacroMapping(
                null, "TEST_PLAT", "Test Platform", "sub2", "[subid_extra]",
                "长token", "ACTIVE", Instant.now(), Instant.now()
        ));

        // 待反解 URL 中同时存在长 token 与短 token
        String rawUrl = "https://adv.com/track?ext=[subid_extra]&sid=[subid]";

        // 执行平台宏 → 标准宏 (standardize)
        AffiliateMacroService.RenderResult result = service.renderToStandard("TEST_PLAT", rawUrl);

        // 验证：长 token [subid_extra] 必须优先被替换为 {sub2}，绝不能被 [subid] 提前破坏成 {sub1}_extra]
        assertEquals("https://adv.com/track?ext={sub2}&sid={sub1}", result.convertedUrl());
        assertEquals(2, result.replaced().size());
    }

    @Test
    @DisplayName("(b) standardize 补 unmapped 反馈测试：精准提取未被映射的宏占位符")
    void testStandardizeReturnsUnmappedTokens() {
        AffiliateMacroService service = new AffiliateMacroService();

        // 传入包含已知 CJ 宏 [ssn] 以及未映射宏 [unmapped_cj_token]、__TIKTOK_UNKNOWN__ 的 URL
        String cjUrl = "https://adv.com/click?sid=[ssn]&extra=[unmapped_cj_token]&track=__TIKTOK_UNKNOWN__";

        AffiliateMacroService.RenderResult result = service.renderToStandard("CJ", cjUrl);

        // [ssn] 应被成功反解为标准宏 {click_id}
        assertTrue(result.convertedUrl().contains("{click_id}"));

        // 未被映射的宏应被收入 unmapped 反馈列表
        assertNotNull(result.unmapped());
        assertFalse(result.unmapped().isEmpty());
        assertTrue(result.unmapped().contains("[unmapped_cj_token]"), "未映射的方括号宏应在 unmapped 列表中反馈");
        assertTrue(result.unmapped().contains("__TIKTOK_UNKNOWN__"), "未映射的双下划线宏应在 unmapped 列表中反馈");
    }

    @Test
    @DisplayName("(c) deleteParam 查询外提与引用拦截保护测试")
    void testDeleteParamHoistsQueryAndProtectsReferences() {
        MacroParamMapper mockParamMapper = mock(MacroParamMapper.class);
        PlatformMacroMappingMapper mockMappingMapper = mock(PlatformMacroMappingMapper.class);

        MacroParamEntity entity = new MacroParamEntity(
                "mcp_test_01", "click_id", "点击ID", "描述",
                "sample", "ATTRIBUTION", "ACTIVE", Instant.now()
        );

        when(mockParamMapper.selectById("mcp_test_01")).thenReturn(entity);

        // 模拟数据库中存在一条引用了 click_id 的平台映射
        PlatformMacroMappingEntity refMapping = new PlatformMacroMappingEntity(
                "pmm_01", "AWIN", "Awin", "click_id", "{clickid}",
                "Awin 点击 ID", "ACTIVE", Instant.now(), Instant.now()
        );
        when(mockMappingMapper.selectList(any())).thenReturn(List.of(refMapping));

        AffiliateMacroService service = new AffiliateMacroService(mockParamMapper, mockMappingMapper);

        // 1. 测试存在关联引用时拦截删除
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.deleteParam("mcp_test_01")
        );
        assertTrue(ex.getMessage().contains("条平台映射引用"));

        // 2. 验证 selectById 只被外提查询了 1 次，绝无循环内重复查库 (N+1)
        verify(mockParamMapper, times(1)).selectById("mcp_test_01");

        // 3. 测试不存在的宏直接返回 false，不执行多余逻辑
        when(mockParamMapper.selectById("mcp_non_existent")).thenReturn(null);
        boolean deleted = service.deleteParam("mcp_non_existent");
        assertFalse(deleted);
    }

    @Test
    @DisplayName("② macro 写库失败向上抛错测试：写库异常不再静默伪装成功")
    void testMacroPersistenceFailureThrowsException() {
        MacroParamMapper mockParamMapper = mock(MacroParamMapper.class);
        PlatformMacroMappingMapper mockMappingMapper = mock(PlatformMacroMappingMapper.class);

        AffiliateMacroService service = new AffiliateMacroService(mockParamMapper, mockMappingMapper);

        // 模拟 saveParam mapper insert 抛异常
        doThrow(new org.springframework.dao.DataIntegrityViolationException("DB constraint error"))
                .when(mockParamMapper).insert(any(MacroParamEntity.class));

        IllegalStateException paramEx = assertThrows(IllegalStateException.class, () ->
                service.saveParam(new MacroParam(null, "test_key", "Test", "Desc", "val", "SYS", "ACTIVE", Instant.now()))
        );
        assertTrue(paramEx.getMessage().contains("保存标准宏至数据库失败"));

        // 模拟 saveMapping mapper insert 抛异常
        doThrow(new org.springframework.dao.DataIntegrityViolationException("DB constraint error"))
                .when(mockMappingMapper).insert(any(PlatformMacroMappingEntity.class));

        IllegalStateException mapEx = assertThrows(IllegalStateException.class, () ->
                service.saveMapping(new PlatformMacroMapping(null, "TEST_P", "Test Platform", "test_key", "{tp_val}", "Desc", "ACTIVE", Instant.now(), Instant.now()))
        );
        assertTrue(mapEx.getMessage().contains("保存平台宏映射至数据库失败"));
    }

    @Test
    @DisplayName("④ 恒等映射不计入 replaced 且支持反向大小写归一化匹配测试")
    void testIdentityMappingIgnoredAndCaseInsensitiveMatch() {
        AffiliateMacroService service = new AffiliateMacroService();

        // 1. 注册一个恒等映射：平台宏 token 与标准宏 token 完全一致均为 {country}
        service.saveMapping(new PlatformMacroMapping(
                null, "TEST_CASE", "Test Case Platform", "country", "{country}",
                "恒等国家映射", "ACTIVE", Instant.now(), Instant.now()
        ));

        // 2. 注册一个小写平台宏 [ssn] -> click_id ({click_id})
        service.saveMapping(new PlatformMacroMapping(
                null, "TEST_CASE", "Test Case Platform", "click_id", "[ssn]",
                "CJ点击宏", "ACTIVE", Instant.now(), Instant.now()
        ));

        // URL 包含：大写的 [SSN]（反向大小写不敏感测试）和恒等宏 {country}
        String rawUrl = "https://adv.com/track?click=[SSN]&geo={country}";

        AffiliateMacroService.RenderResult result = service.renderToStandard("TEST_CASE", rawUrl);

        // 验证反向匹配支持大小写归一化：[SSN] 成功转换为 {click_id}
        assertTrue(result.convertedUrl().contains("click={click_id}"), "反向匹配应忽略大小写并将 [SSN] 替换为 {click_id}");

        // 验证恒等映射：{country} 虽被命中但未实质变更，不计入 replaced 列表
        assertTrue(result.replaced().stream().anyMatch(r -> "click_id".equals(r.get("macroKey"))), "实际发生有效替换的宏 click_id 应计入 replaced");
        assertFalse(result.replaced().stream().anyMatch(r -> "country".equals(r.get("macroKey"))), "恒等映射 {country} -> {country} 不应计入 replaced");
        assertEquals(1, result.replaced().size(), "replaced 集合大小应为 1");
    }
}
