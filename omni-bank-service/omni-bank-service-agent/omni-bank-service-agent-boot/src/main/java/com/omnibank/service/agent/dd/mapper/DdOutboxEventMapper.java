package com.omnibank.service.agent.dd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnibank.service.agent.dd.entity.DdOutboxEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * Outbox 事件
 */
@Mapper
public interface DdOutboxEventMapper extends BaseMapper<DdOutboxEvent> {
}
