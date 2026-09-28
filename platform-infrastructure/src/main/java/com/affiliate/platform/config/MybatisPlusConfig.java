package com.affiliate.platform.config;

import com.affiliate.platform.tenant.TenantContext;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * MyBatis-Plus 核心自动装配与插件配置类 (MyBatis-Plus Configuration for PostgreSQL)
 * <p>
 * 1. 扫描 `com.affiliate.platform.mapper` 包下所有继承自 `BaseMapper` 的 Mapper 接口；
 * 2. 装配 TenantLineInnerInterceptor 实现全系统 SQL 级别的强租户隔离防御；
 * 3. 装配 PostgreSQL 数据库方言的分页拦截器（PaginationInnerInterceptor）与乐观锁拦截器；
 * 4. 装配防全表更新与删除拦截器（BlockAttackInnerInterceptor），防止误全量更新生产数据。
 */
@Configuration
@MapperScan("com.affiliate.platform.mapper")
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true", matchIfMissing = true)
public class MybatisPlusConfig {

    private static final Set<String> TENANT_TABLES = Set.of(
            "affiliate_partner",
            "affiliate_offer",
            "affiliate_smart_link",
            "affiliate_click_session",
            "affiliate_conversion",
            "affiliate_invoice",
            "affiliate_sub_id_stats",
            "affiliate_offer_goal",
            "affiliate_antifraud_blacklist",
            "affiliate_antifraud_audit_log",
            "billing_entry",
            "billing_payout_batch",
            "billing_payout_item",
            "campaign",
            "campaign_budget",
            "creative",
            "ad_slot",
            "auction",
            "partner_connection",
            "budget_reservation",
            "cdp_profile",
            "cdp_customer_event",
            "cdp_identity_graph",
            "cdp_user_trait_state",
            "dmp_segment",
            "dmp_segment_member",
            "report_daily",
            "report_cohort_acquisition",
            "report_cohort_activity",
            "wallet_account",
            "api_key"
    );

    /**
     * 注册 MyBatis-Plus 综合拦截器链（支持多租户行级隔离、PostgreSQL 物理分页、并发乐观锁与防全表操作）
     *
     * @return 拦截器链 Bean
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 多租户行级隔离拦截器 (强制在 SQL 注入 WHERE tenant_id = ?)
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                String tenant = TenantContext.get();
                return new StringValue(tenant != null && !tenant.isBlank() ? tenant : "public");
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (tableName == null) {
                    return true;
                }
                String lower = tableName.toLowerCase();
                // 仅对明确含有 tenant_id 的核心业务表应用行级隔离
                return !TENANT_TABLES.contains(lower);
            }
        }));

        // 2. PostgreSQL 物理分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        paginationInterceptor.setMaxLimit(1000L); // 限制单次最大查询 1000 条，防 OOM 内存溢出
        paginationInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInterceptor);

        // 3. 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 4. 防全表更新与全表删除插件
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}

