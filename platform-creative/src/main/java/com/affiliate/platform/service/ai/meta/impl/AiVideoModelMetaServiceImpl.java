package com.affiliate.platform.service.ai.meta.impl;

import com.affiliate.platform.domain.ai.*;
import com.affiliate.platform.service.ai.meta.AiVideoModelMetaService;
import com.affiliate.platform.entity.CreativeAiModelMetaEntity;
import com.affiliate.platform.mapper.CreativeAiModelMetaMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频模型元数据服务实现类 (支持动态数据库计费配置与内置模型元数据)
 */
@Service
public class AiVideoModelMetaServiceImpl implements AiVideoModelMetaService {

    private final CreativeAiModelMetaMapper modelMetaMapper;
    private final Map<String, AiVideoModelMetaDTO> modelRegistry = new ConcurrentHashMap<>();

    public AiVideoModelMetaServiceImpl() {
        this(null);
    }

    @Autowired
    public AiVideoModelMetaServiceImpl(@Autowired(required = false) CreativeAiModelMetaMapper modelMetaMapper) {
        this.modelMetaMapper = modelMetaMapper;
        initDefaultModels();
    }

    private void initDefaultModels() {
        // 1. 字节跳动 Seedance 2.0
        AiVideoModelMetaDTO seedance = new AiVideoModelMetaDTO(
                "ark_seedance_2_0",
                "字节跳动 Seedance 2.0 (高清画质)",
                "BYTEDANCE_ARK",
                "seedance-2.0-pro",
                List.of(AiGenerateType.TEXT_TO_VIDEO, AiGenerateType.IMAGE_TO_VIDEO, AiGenerateType.REFERENCE_TO_VIDEO),
                "SECOND",
                BigDecimal.valueOf(0.1500),
                BigDecimal.ONE,
                true,
                List.of(
                        new AiVideoModelFieldDTO("aspectRatio", "画幅比例", "select", true, "9:16", "推荐短视频画幅",
                                List.of(
                                        AiVideoModelFieldOptionDTO.ofDefault("竖屏 9:16 (推荐短视频)", "9:16"),
                                        AiVideoModelFieldOptionDTO.of("横屏 16:9", "16:9"),
                                        AiVideoModelFieldOptionDTO.of("正方形 1:1", "1:1")
                                )),
                        new AiVideoModelFieldDTO("durationSeconds", "生成时长", "select", true, 5, "单镜时长秒数",
                                List.of(
                                        AiVideoModelFieldOptionDTO.ofDefault("5 秒 (标准镜头)", 5),
                                        AiVideoModelFieldOptionDTO.of("10 秒 (长动作镜头)", 10)
                                )),
                        new AiVideoModelFieldDTO("motionStrength", "动作幅度", "select", false, "medium", "画面肢体运动幅度",
                                List.of(
                                        AiVideoModelFieldOptionDTO.of("轻微动作 (微表情/呼吸感)", "low"),
                                        AiVideoModelFieldOptionDTO.ofDefault("标准动作 (常规行走/动作)", "medium"),
                                        AiVideoModelFieldOptionDTO.of("剧烈动作 (快节奏/运镜切换)", "high")
                                ))
                )
        );
        modelRegistry.put(seedance.modelCode(), seedance);

        // 2. 阿里百炼 Wan 2.1
        AiVideoModelMetaDTO wan = new AiVideoModelMetaDTO(
                "dashscope_wan_2_1",
                "阿里百炼 Wan 2.1 (通义万相)",
                "DASHSCOPE",
                "wan2.1-t2v-plus",
                List.of(AiGenerateType.TEXT_TO_VIDEO, AiGenerateType.IMAGE_TO_VIDEO),
                "SECOND",
                BigDecimal.valueOf(0.2000),
                BigDecimal.ONE,
                true,
                List.of(
                        new AiVideoModelFieldDTO("aspectRatio", "画幅比例", "select", true, "9:16", "生成比例",
                                List.of(
                                        AiVideoModelFieldOptionDTO.ofDefault("竖屏 9:16", "9:16"),
                                        AiVideoModelFieldOptionDTO.of("横屏 16:9", "16:9")
                                )),
                        new AiVideoModelFieldDTO("durationSeconds", "时长", "select", true, 5, "生成时长",
                                List.of(AiVideoModelFieldOptionDTO.ofDefault("5 秒", 5)))
                )
        );
        modelRegistry.put(wan.modelCode(), wan);

        // 3. HappyHorse 1.1 视频编辑
        AiVideoModelMetaDTO happyHorse = new AiVideoModelMetaDTO(
                "happyhorse_1_1",
                "HappyHorse 1.1 (视频编辑与指令重绘)",
                "DASHSCOPE",
                "happyhorse-v1.1-edit",
                List.of(AiGenerateType.VIDEO_EDIT, AiGenerateType.REFERENCE_TO_VIDEO),
                "SECOND",
                BigDecimal.valueOf(0.1800),
                BigDecimal.ONE,
                true,
                List.of(
                        new AiVideoModelFieldDTO("sourceVideoUrl", "原视频地址", "text", true, "", "需要被编辑重绘的源视频 URL", List.of())
                )
        );
        modelRegistry.put(happyHorse.modelCode(), happyHorse);

        // 4. 快手可灵 Kling v1 (支持自治重构与长视频分镜渲染)
        AiVideoModelMetaDTO kling = new AiVideoModelMetaDTO(
                "kling_v1",
                "快手可灵 Kling 1.6 Pro (电影级运镜)",
                "BYTEDANCE_ARK",
                "kling-v1-6-pro",
                List.of(AiGenerateType.TEXT_TO_VIDEO, AiGenerateType.IMAGE_TO_VIDEO, AiGenerateType.REFERENCE_TO_VIDEO),
                "SECOND",
                BigDecimal.valueOf(0.1600),
                BigDecimal.ONE,
                true,
                List.of(
                        new AiVideoModelFieldDTO("aspectRatio", "画幅比例", "select", true, "9:16", "生成比例",
                                List.of(
                                        AiVideoModelFieldOptionDTO.ofDefault("竖屏 9:16", "9:16"),
                                        AiVideoModelFieldOptionDTO.of("横屏 16:9", "16:9")
                                )),
                        new AiVideoModelFieldDTO("durationSeconds", "时长", "select", true, 5, "生成时长",
                                List.of(
                                        AiVideoModelFieldOptionDTO.ofDefault("5 秒", 5),
                                        AiVideoModelFieldOptionDTO.of("10 秒", 10)
                                ))
                )
        );
        modelRegistry.put(kling.modelCode(), kling);

        // 5. Local Mock 测试模型
        AiVideoModelMetaDTO mock = new AiVideoModelMetaDTO(
                "local_mock_video",
                "Local Mock Video Model (本地测试极速模型)",
                "LOCAL_MOCK",
                "mock-fast-v1",
                List.of(AiGenerateType.values()),
                "SECOND",
                BigDecimal.ZERO,
                BigDecimal.ONE,
                true,
                List.of()
        );
        modelRegistry.put(mock.modelCode(), mock);
    }

    @Override
    public List<AiVideoModelMetaDTO> listAvailableModels() {
        return modelRegistry.keySet().stream()
                .map(this::getModel)
                .flatMap(Optional::stream)
                .filter(AiVideoModelMetaDTO::enabled)
                .toList();
    }

    @Override
    public Optional<AiVideoModelMetaDTO> getModel(String modelCode) {
        if (modelCode == null) return Optional.empty();
        String code = modelCode.toLowerCase().trim();
        AiVideoModelMetaDTO defaultMeta = modelRegistry.get(code);

        if (modelMetaMapper != null) {
            try {
                QueryWrapper<CreativeAiModelMetaEntity> qw = new QueryWrapper<>();
                qw.eq("model_code", code).last("LIMIT 1");
                CreativeAiModelMetaEntity entity = modelMetaMapper.selectOne(qw);
                if (entity != null) {
                    if (defaultMeta != null) {
                        // 动态覆盖数据库中的价格与启用状态
                        return Optional.of(new AiVideoModelMetaDTO(
                                defaultMeta.modelCode(),
                                entity.getModelName() != null ? entity.getModelName() : defaultMeta.modelName(),
                                defaultMeta.providerCode(),
                                defaultMeta.upstreamModelId(),
                                defaultMeta.supportedTypes(),
                                entity.getBillingUnit() != null ? entity.getBillingUnit() : defaultMeta.billingUnit(),
                                entity.getUnitPrice() != null ? entity.getUnitPrice() : defaultMeta.unitPrice(),
                                entity.getPricingMultiplier() != null ? entity.getPricingMultiplier() : defaultMeta.pricingMultiplier(),
                                Boolean.TRUE.equals(entity.getEnabled()),
                                defaultMeta.fields()
                        ));
                    }
                }
            } catch (Exception ignored) {}
        }

        return Optional.ofNullable(defaultMeta);
    }

    @Override
    public void validateRequest(VideoGenerateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("generate request cannot be null");
        }
        AiVideoModelMetaDTO meta = getModel(request.modelCode())
                .orElseThrow(() -> new IllegalArgumentException("Unsupported or disabled modelCode: " + request.modelCode()));

        // 1. 校验生成类型是否支持
        if (!meta.supports(request.generateType())) {
            throw new IllegalArgumentException(String.format("Model %s does not support generateType: %s",
                    request.modelCode(), request.generateType()));
        }

        // 2. 图生视频校验：起始图必填
        if (request.generateType() == AiGenerateType.IMAGE_TO_VIDEO) {
            if (request.startImageUrl() == null || request.startImageUrl().isBlank()) {
                throw new IllegalArgumentException("startImageUrl is required for IMAGE_TO_VIDEO generateType");
            }
        }

        // 3. 视频编辑校验：原视频必填
        if (request.generateType() == AiGenerateType.VIDEO_EDIT) {
            if (request.sourceVideoUrl() == null || request.sourceVideoUrl().isBlank()) {
                throw new IllegalArgumentException("sourceVideoUrl is required for VIDEO_EDIT generateType");
            }
        }

        // 4. 时长限制校验
        if (request.durationSeconds() < 3 || request.durationSeconds() > 15) {
            throw new IllegalArgumentException(String.format("durationSeconds must be between 3 and 15, got %d",
                    request.durationSeconds()));
        }
    }
}
