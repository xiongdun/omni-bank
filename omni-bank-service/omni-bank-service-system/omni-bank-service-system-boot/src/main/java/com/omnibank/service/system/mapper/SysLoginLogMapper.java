package com.omnibank.service.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnibank.service.system.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/** 登录日志 Mapper */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
