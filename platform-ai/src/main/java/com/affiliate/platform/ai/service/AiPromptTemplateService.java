package com.affiliate.platform.ai.service;

import com.affiliate.platform.ai.model.AiPromptTemplate;
import com.affiliate.platform.entity.AiPromptTemplateEntity;
import com.affiliate.platform.mapper.AiPromptTemplateMapper;
import com.affiliate.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结构化 Prompt 模板管理与渲染服务 (AI Prompt Template Service)
 */
@Service
public class AiPromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(AiPromptTemplateService.class);

    public static final String TEMPLATE_CREATIVE_DIAGNOSIS = "CREATIVE_DIAGNOSIS_AND_REFINE";
    public static final String TEMPLATE_AD_COPY_EXPANSION = "AD_COPY_EXPANSION";
    public static final String TEMPLATE_FRAUD_EXPLANATION = "FRAUD_ANOMALY_EXPLANATION";
    public static final String TEMPLATE_BID_ADVISORY = "CAMPAIGN_BID_ADVISORY";
    public static final String TEMPLATE_REPORT_SYNTHESIS = "REPORT_INSIGHT_SYNTHESIS";

    private final Map<String, AiPromptTemplate> store = new ConcurrentHashMap<>();
    private final AiPromptTemplateMapper templateMapper;

    @Autowired
    public AiPromptTemplateService(@Autowired(required = false) AiPromptTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
        initDefaultTemplates();
        preloadFromDatabase();
    }

    public AiPromptTemplateService() {
        this(null);
    }

    private void preloadFromDatabase() {
        if (templateMapper != null) {
            try {
                List<AiPromptTemplateEntity> list = templateMapper.selectList(null);
                if (list != null && !list.isEmpty()) {
                    for (AiPromptTemplateEntity e : list) {
                        store.put(buildStoreKey(e.getTenantId(), e.getTemplateKey()), new AiPromptTemplate(
                                e.getId(), e.getTenantId(), e.getTemplateKey(), e.getTitle(),
                                e.getSystemPrompt(), e.getUserPromptTemplate(), e.getModelPreference(),
                                e.getCreatedAt(), e.getUpdatedAt()
                        ));
                    }
                }
            } catch (Exception e) {
                log.debug("[AiPromptTemplateService] Database preload skipped: {}", e.getMessage());
            }
        }
    }

    private void initDefaultTemplates() {
        // 1. 创意自主诊断与重构模板
        registerDefault(
                TEMPLATE_CREATIVE_DIAGNOSIS,
                "广告素材 AI 诊断与自省重构模板",
                "你是一位资深的信息流与短视频广告创意专家，精通高跳出率诊断、钩子情绪唤醒与强行动指令重构。",
                """
                【待诊断素材特征】
                素材编号: {{materialId}}
                目标受众: {{targetAudience}}
                退化诱因: {{diagnosisReason}}
                当前脚本片段: {{currentPrompt}}
                
                请输出结构化的优化建议：
                1. 痛点归因与用户注意力流失原因分析；
                2. 重构前3秒黄金Hook话术（具备强冲突与悬念）；
                3. 强化中段产品利益点与行动指令（CTA）；
                4. 视频节奏、音频贴纸与画中画的剪辑指导。
                """
        );

        // 2. 营销文案衍生扩展模板
        registerDefault(
                TEMPLATE_AD_COPY_EXPANSION,
                "爆款广告标题与文案多维变体扩展",
                "你是一位专业的出海/国内效果类效果营销文案大师，擅长利用 AIDA 模型裂变高转化标题与 Hook 文案。",
                """
                【推广产品背景】
                产品名/类别: {{productName}}
                受众画像: {{targetAudience}}
                核心价值主张: {{sellingPoints}}
                目标转化动作: {{callToAction}}
                
                请提供 3 组具有高完播率与高点击率的广告文案变体，每组需包含：
                - 黄金前3秒注意力钩子（Hook）
                - 15秒核心痛点场景剧本
                - 强驱动转化落地 CTA 文案
                """
        );

        // 3. 反作弊异常归因与处置建议模板
        registerDefault(
                TEMPLATE_FRAUD_EXPLANATION,
                "智能反作弊欺诈行为分析与风控诊断",
                "你是一位具备顶级经验的数字广告反作弊与风控安全分析专家，精通机房刷量、点击农场、快速回传（Click-to-Install Time Anomaly）与签名篡改的指纹鉴定。",
                """
                【异常追踪日志】
                点击编号: {{clickId}}
                触动作弊特征: {{fraudReason}}
                风控遥测数据: {{telemetrySummary}}
                
                请给出：
                1. 欺诈手法定性（如 Click Flooding、Datacenter Proxy 伪造等）；
                2. 风险等级判定（LOW/MEDIUM/HIGH/CRITICAL）；
                3. 具体拦截或佣金追回操作建议；
                4. 针对关联发布商（Sub-ID）的长效治理方案。
                """
        );

        // 4. DSP 智能出价与预算策略咨询模板
        registerDefault(
                TEMPLATE_BID_ADVISORY,
                "DSP 智能出价与拍卖竞价优化建议",
                "你是一位 OpenRTB 2.5 计算广告智能竞价算法专家，深谙一阶暗标拍卖机制（First-Price Auction）与 Bid Shading 成本压缩博弈。",
                """
                【竞价环境特征】
                广告计划: {{campaignId}}
                当前基准出价: ${{currentBid}}
                预估点击率 (eCTR): {{eCtr}}
                预估转化率 (eCvr): {{eCvr}}
                竞价底价 (Floor Price): ${{floorPrice}}
                最近胜出率 (Win Rate): {{winRate}}
                
                请输出：
                1. 针对当前流量底价与胜出率的博弈健康度评估；
                2. 基于期望估值（eCTR * eCVR * Payout）的最佳出价调整系数建议；
                3. 预算平滑消耗（Budget Pacing）与分时段定向调优方案。
                """
        );

        // 5. 商业智能与经营报表总结模板
        registerDefault(
                TEMPLATE_REPORT_SYNTHESIS,
                "广告经营分析与高管商业洞察生成",
                "你是一位广告联盟（Affiliate Network）商业分析官，擅长从曝光、点击、转化、ROAS 等多维海量指标中提炼商业结论与异常预警。",
                """
                【汇总数据周期与指标】
                统计周期: {{dateRange}}
                多维指标快照: {{metricsSnapshot}}
                
                请生成一份条理清晰、具备行动指引的经营分析摘要：
                1. 业务大盘表现亮点与利润核心驱动源；
                2. 潜在毛利下滑或长尾流量流失风险；
                3. 下一阶段可执行的预算调整与投放优化清单。
                """
        );
    }

    private void registerDefault(String key, String title, String systemPrompt, String userTemplate) {
        AiPromptTemplate pt = new AiPromptTemplate(
                "ptpl_global_" + key.toLowerCase(),
                "default",
                key,
                title,
                systemPrompt,
                userTemplate,
                null,
                Instant.now(),
                Instant.now()
        );
        store.put(buildStoreKey("default", key), pt);
    }

    private String buildStoreKey(String tenantId, String key) {
        return (tenantId != null && !tenantId.isBlank() ? tenantId : "default") + ":" + key;
    }

    public List<AiPromptTemplate> listTemplates(String tenantId) {
        String targetTenant = (tenantId != null && !tenantId.isBlank())
                ? tenantId
                : (TenantContext.get() != null ? TenantContext.get() : "default");
        Map<String, AiPromptTemplate> result = new LinkedHashMap<>();

        // 先载入 default 模板，再用租户级自定义模板覆盖
        store.forEach((k, v) -> {
            if ("default".equals(v.tenantId())) {
                result.put(v.templateKey(), v);
            }
        });
        store.forEach((k, v) -> {
            if (targetTenant.equals(v.tenantId())) {
                result.put(v.templateKey(), v);
            }
        });

        return new ArrayList<>(result.values());
    }

    public Optional<AiPromptTemplate> findTemplate(String tenantId, String templateKey) {
        String tid = (tenantId != null && !tenantId.isBlank())
                ? tenantId
                : (TenantContext.get() != null ? TenantContext.get() : "default");
        AiPromptTemplate custom = store.get(buildStoreKey(tid, templateKey));
        if (custom != null) {
            return Optional.of(custom);
        }
        return Optional.ofNullable(store.get(buildStoreKey("default", templateKey)));
    }

    public AiPromptTemplate saveOrUpdate(AiPromptTemplate template) {
        if (template == null || template.templateKey() == null) {
            throw new IllegalArgumentException("Template and templateKey cannot be null");
        }
        String tid = (template.tenantId() != null && !template.tenantId().isBlank())
                ? template.tenantId()
                : (TenantContext.get() != null ? TenantContext.get() : "default");
        String storeKey = buildStoreKey(tid, template.templateKey());
        AiPromptTemplate existing = store.get(storeKey);

        // 若该租户同 key 模板已存在，则复用其主键 ID，防止触发数据库 UNIQUE(tenant_id, template_key) 约束冲突
        String id = existing != null
                ? existing.id()
                : (template.id() != null && !template.id().isBlank() && !template.id().startsWith("ptpl_global_")
                    ? template.id()
                    : "ptpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));

        AiPromptTemplate toSave = new AiPromptTemplate(
                id,
                tid,
                template.templateKey(),
                template.title() != null ? template.title() : template.templateKey(),
                template.systemPrompt() != null ? template.systemPrompt() : "You are a professional ad-tech AI assistant.",
                template.userPromptTemplate() != null ? template.userPromptTemplate() : "",
                template.modelPreference(),
                existing != null ? existing.createdAt() : Instant.now(),
                Instant.now()
        );

        store.put(storeKey, toSave);
        if (templateMapper != null) {
            try {
                AiPromptTemplateEntity entity = new AiPromptTemplateEntity(
                        toSave.id(), toSave.tenantId(), toSave.templateKey(), toSave.title(),
                        toSave.systemPrompt(), toSave.userPromptTemplate(), toSave.modelPreference(),
                        toSave.createdAt(), toSave.updatedAt()
                );
                if (templateMapper.selectById(toSave.id()) != null) {
                    templateMapper.updateById(entity);
                } else {
                    templateMapper.insert(entity);
                }
            } catch (Exception e) {
                log.debug("[AiPromptTemplateService] Database write skipped: {}", e.getMessage());
            }
        }
        return toSave;
    }

    public String renderPrompt(String tenantId, String templateKey, Map<String, Object> variables) {
        Optional<AiPromptTemplate> pt = findTemplate(tenantId, templateKey);
        if (pt.isEmpty()) {
            return variables != null ? variables.toString() : "";
        }
        return pt.get().renderUserPrompt(variables);
    }
}
