package com.omnibank.service.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnibank.service.system.entity.SysUserPost;
import org.apache.ibatis.annotations.Mapper;

/** 用户-岗位关联 Mapper */
@Mapper
public interface SysUserPostMapper extends BaseMapper<SysUserPost> {
}
