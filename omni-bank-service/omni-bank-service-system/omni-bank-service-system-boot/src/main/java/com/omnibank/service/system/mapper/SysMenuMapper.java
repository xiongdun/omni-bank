package com.omnibank.service.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnibank.service.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;

/** 菜单/权限点 Mapper */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
}
