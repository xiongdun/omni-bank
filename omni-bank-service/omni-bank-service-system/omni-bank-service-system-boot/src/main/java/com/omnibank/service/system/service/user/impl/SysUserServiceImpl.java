package com.omnibank.service.system.service.user.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.system.api.enums.SysErrorCode;
import com.omnibank.service.system.dto.user.UserCreateReq;
import com.omnibank.service.system.dto.user.UserQueryReq;
import com.omnibank.service.system.dto.user.UserUpdateReq;
import com.omnibank.service.system.entity.SysConfig;
import com.omnibank.service.system.entity.SysUser;
import com.omnibank.service.system.entity.SysUserPost;
import com.omnibank.service.system.entity.SysUserRole;
import com.omnibank.service.system.mapper.SysUserMapper;
import com.omnibank.service.system.service.user.SysUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务实现（MyBatis-Plus ServiceImpl 体系）：
 * 主实体经 this.lambdaQuery()/save/updateById/removeById 链式操作；
 * 关联表（用户-角色/用户-岗位、系统设置）经 Db 工具类批量操作，替代逐条 insert。
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final String INIT_PASSWORD_CONFIG_KEY = "sys.user.init.password";

    private final PasswordEncoder passwordEncoder;

    public SysUserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public IPage<SysUser> page(UserQueryReq req) {
        return lambdaQuery()
                .like(req.getUsername() != null, SysUser::getUsername, req.getUsername())
                .like(req.getNickname() != null, SysUser::getNickname, req.getNickname())
                .eq(req.getStatus() != null, SysUser::getStatus, req.getStatus())
                .eq(req.getOrgId() != null, SysUser::getOrgId, req.getOrgId())
                .orderByDesc(SysUser::getCreateTime)
                .page(new Page<>(req.getCurrent(), req.getSize()));
    }

    @Override
    public SysUser getById(Long id) {
        SysUser user = super.getById(id);
        if (user == null) {
            throw new BizException(SysErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public SysUser getByUsername(String username) {
        return lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(UserCreateReq req) {
        if (lambdaQuery().eq(SysUser::getUsername, req.getUsername()).exists()) {
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
        save(user);
        assignRoles(user.getId(), req.getRoleIds());
        assignPosts(user.getId(), req.getPostIds());
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
        // MP updateById 仅更新非 null 字段
        updateById(user);
        assignRoles(req.getId(), req.getRoleIds());
        assignPosts(req.getId(), req.getPostIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        // @TableLogic 自动逻辑删除，并清理角色/岗位关联
        removeById(id);
        Db.remove(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        Db.remove(Wrappers.<SysUserPost>lambdaQuery().eq(SysUserPost::getUserId, id));
    }

    @Override
    public void resetPassword(Long id) {
        getById(id);
        String initPassword = getConfigValue(INIT_PASSWORD_CONFIG_KEY, "123456");
        lambdaUpdate()
                .set(SysUser::getPassword, passwordEncoder.encode(initPassword))
                .eq(SysUser::getId, id)
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        // 全量覆盖：先清空后批量写入
        Db.remove(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        if (!roleIds.isEmpty()) {
            Db.saveBatch(roleIds.stream().map(roleId -> {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                return userRole;
            }).toList());
        }
    }

    /** 全量覆盖用户岗位 */
    private void assignPosts(Long userId, List<Long> postIds) {
        if (postIds == null) {
            return;
        }
        Db.remove(Wrappers.<SysUserPost>lambdaQuery().eq(SysUserPost::getUserId, userId));
        if (!postIds.isEmpty()) {
            Db.saveBatch(postIds.stream().map(postId -> {
                SysUserPost userPost = new SysUserPost();
                userPost.setUserId(userId);
                userPost.setPostId(postId);
                return userPost;
            }).toList());
        }
    }

    /** 读取系统设置参数值，缺失时返回默认值 */
    private String getConfigValue(String configKey, String defaultValue) {
        SysConfig config = Db.getOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, configKey));
        return config == null ? defaultValue : config.getConfigValue();
    }
}
