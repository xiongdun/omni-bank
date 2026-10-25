package com.omnibank.service.system.service.user.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.system.api.enums.SysErrorCode;
import com.omnibank.service.system.dto.user.UserCreateReq;
import com.omnibank.service.system.dto.user.UserQueryReq;
import com.omnibank.service.system.dto.user.UserUpdateReq;
import com.omnibank.service.system.entity.SysConfig;
import com.omnibank.service.system.entity.SysUser;
import com.omnibank.service.system.entity.SysUserPost;
import com.omnibank.service.system.entity.SysUserRole;
import com.omnibank.service.system.mapper.SysConfigMapper;
import com.omnibank.service.system.mapper.SysUserMapper;
import com.omnibank.service.system.mapper.SysUserPostMapper;
import com.omnibank.service.system.mapper.SysUserRoleMapper;
import com.omnibank.service.system.service.user.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务实现：查重/BCrypt 加密/角色岗位分配/重置密码（初始密码取系统设置）。
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private static final String INIT_PASSWORD_CONFIG_KEY = "sys.user.init.password";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysConfigMapper configMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public IPage<SysUser> page(UserQueryReq req) {
        return userMapper.selectPage(new Page<>(req.getCurrent(), req.getSize()),
                Wrappers.<SysUser>lambdaQuery()
                        .like(req.getUsername() != null, SysUser::getUsername, req.getUsername())
                        .like(req.getNickname() != null, SysUser::getNickname, req.getNickname())
                        .eq(req.getStatus() != null, SysUser::getStatus, req.getStatus())
                        .eq(req.getOrgId() != null, SysUser::getOrgId, req.getOrgId())
                        .orderByDesc(SysUser::getCreateTime));
    }

    @Override
    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(SysErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public SysUser getByUsername(String username) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(UserCreateReq req) {
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, req.getUsername()));
        if (count > 0) {
            throw new BizException(SysErrorCode.USER_ALREADY_EXISTS);
        }
        SysUser user = new SysUser();
        user.setOrgId(req.getOrgId());
        user.setUsername(req.getUsername());
        user.setNickname(req.getNickname());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setUserType(req.getUserType());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setStatus(req.getStatus());
        user.setRemark(req.getRemark());
        userMapper.insert(user);
        if (req.getRoleIds() != null) {
            assignRoles(user.getId(), req.getRoleIds());
        }
        if (req.getPostIds() != null) {
            assignPosts(user.getId(), req.getPostIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserUpdateReq req) {
        getById(req.getId());
        SysUser user = new SysUser();
        user.setId(req.getId());
        user.setNickname(req.getNickname());
        user.setOrgId(req.getOrgId());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setStatus(req.getStatus());
        user.setRemark(req.getRemark());
        userMapper.updateById(user);
        if (req.getRoleIds() != null) {
            assignRoles(req.getId(), req.getRoleIds());
        }
        if (req.getPostIds() != null) {
            assignPosts(req.getId(), req.getPostIds());
        }
    }

    @Override
    public void delete(Long id) {
        getById(id);
        userMapper.deleteById(id);
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        userPostMapper.delete(Wrappers.<SysUserPost>lambdaQuery().eq(SysUserPost::getUserId, id));
    }

    @Override
    public void resetPassword(Long id) {
        getById(id);
        String initPassword = getConfigValue(INIT_PASSWORD_CONFIG_KEY, "123456");
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(initPassword));
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            roleIds.forEach(roleId -> {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRoleMapper.insert(userRole);
            });
        }
    }

    /** 全量覆盖用户岗位 */
    private void assignPosts(Long userId, List<Long> postIds) {
        userPostMapper.delete(Wrappers.<SysUserPost>lambdaQuery().eq(SysUserPost::getUserId, userId));
        if (postIds != null) {
            postIds.forEach(postId -> {
                SysUserPost userPost = new SysUserPost();
                userPost.setUserId(userId);
                userPost.setPostId(postId);
                userPostMapper.insert(userPost);
            });
        }
    }

    /** 读取系统设置参数值，缺失时返回默认值 */
    private String getConfigValue(String configKey, String defaultValue) {
        SysConfig config = configMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, configKey));
        return config == null ? defaultValue : config.getConfigValue();
    }
}
