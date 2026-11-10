package com.omnibank.service.agent.dd.adapter;

import java.util.List;

/**
 * 外部数据适配器：银行现有系统与外部数据平台的唯一接入点。
 * 真实接口未确认前只允许提供 Mock 实现，且必须通过 {@link #mock()} 与来源标识显式暴露模拟性质。
 */
public interface ExternalDataAdapter {

    /**
     * 适配器名称，用于日志与失败定位
     */
    String name();

    /**
     * 是否模拟适配器：Mock 数据必须显式标记，不得伪装为真实接口
     */
    boolean mock();

    /**
     * 采集指定企业的基础外部数据
     */
    List<ExternalDataItem> collect(ExternalDataQuery query);
}
