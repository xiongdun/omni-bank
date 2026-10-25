package com.omnibank.service.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnibank.service.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/** 用户-角色关联 Mapper */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
