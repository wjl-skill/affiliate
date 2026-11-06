package com.affiliate.platform.nacos;

import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.Map;

/**
 * Nacos 动态配置热更新事件
 * <p>
 * 当 Nacos 配置中心的 DataId 发生变更并推送至微服务节点时触发，
 * 业务组件（如 LinUCB Bandit 引擎、反作弊规则引擎、补量引擎）可监听该事件实时热更新内存运行参数。
 */
public class NacosConfigRefreshEvent extends ApplicationEvent {

    private final String dataId;
    private final String group;
    private final Map<String, Object> updatedProperties;

    public NacosConfigRefreshEvent(Object source, String dataId, String group, Map<String, Object> updatedProperties) {
        super(source);
        this.dataId = dataId;
        this.group = group;
        this.updatedProperties = updatedProperties != null ? Collections.unmodifiableMap(updatedProperties) : Map.of();
    }

    public String getDataId() {
        return dataId;
    }

    public String getGroup() {
        return group;
    }

    public Map<String, Object> getUpdatedProperties() {
        return updatedProperties;
    }
}
