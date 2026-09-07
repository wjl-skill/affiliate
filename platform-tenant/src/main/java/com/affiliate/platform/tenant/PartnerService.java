package com.affiliate.platform.service;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.PartnerConnection;
import com.affiliate.platform.repository.Repository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 合作方连接管理服务 (Partner Connection Service)
 * <p>
 * 维护系统与外部 DSP、SSP 及 ADX 节点的端点连接、鉴权密钥与在线同步状态。
 */
@Service
public class PartnerService {

    // 合作方持久化仓储
    private final Repository<PartnerConnection> repository;

    public PartnerService(Repository<PartnerConnection> repository) {
        this.repository = repository;
    }

    /**
     * 注册接入新的外部合作方连接
     *
     * @param input 合作方信息
     * @return 状态为 ACTIVE 的连接记录
     */
    public PartnerConnection create(PartnerConnection input) {
        return repository.save(new PartnerConnection(
                repository.nextId("partner"),
                input.name(),
                input.type(),
                input.endpoint(),
                input.settings(),
                ConnectionStatus.ACTIVE,
                Instant.now()
        ));
    }

    /**
     * 获取全量配置的合作方连接
     *
     * @return 合作方列表
     */
    public List<PartnerConnection> list() {
        return repository.findAll();
    }

    /**
     * 根据 ID 检索合作方配置
     *
     * @param id 合作方标识
     * @return 合作方实体
     */
    public PartnerConnection get(String id) {
        return repository.find(id)
                .orElseThrow(() -> new NotFoundException("partner", id));
    }

    /**
     * 变更合作方连接状态（如手动切为 PAUSED 或 ERROR）
     *
     * @param id     合作方标识
     * @param status 新状态
     * @return 更新后的连接记录
     */
    public PartnerConnection setStatus(String id, ConnectionStatus status) {
        return repository.save(get(id).withStatus(status));
    }
}
