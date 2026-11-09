package com.omnibank.service.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnibank.service.agent.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/** 会话 Mapper */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
