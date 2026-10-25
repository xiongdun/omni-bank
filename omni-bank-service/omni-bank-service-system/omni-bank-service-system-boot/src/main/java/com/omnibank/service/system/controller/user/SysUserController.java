package com.omnibank.service.system.controller.user;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.common.core.result.Result;
import com.omnibank.service.system.dto.user.UserCreateReq;
import com.omnibank.service.system.dto.user.UserQueryReq;
import com.omnibank.service.system.dto.user.UserUpdateReq;
import com.omnibank.service.system.entity.SysUser;
import com.omnibank.service.system.service.user.SysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    /** 分页查询用户 */
    @SaCheckPermission("sys:user:list")
    @GetMapping("/page")
    public Result<IPage<SysUser>> page(UserQueryReq req) {
        return Result.success(userService.page(req));
    }

    /** 用户详情 */
    @SaCheckPermission("sys:user:query")
    @GetMapping("/{id}")
    public Result<SysUser> get(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    /** 新增用户 */
    @SaCheckPermission("sys:user:add")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody UserCreateReq req) {
        userService.create(req);
        return Result.success();
    }

    /** 修改用户（含角色/岗位重分配） */
    @SaCheckPermission("sys:user:edit")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody UserUpdateReq req) {
        userService.update(req);
        return Result.success();
    }

    /** 删除用户（逻辑删除） */
    @SaCheckPermission("sys:user:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    /** 重置密码（初始密码取系统设置 sys.user.init.password） */
    @SaCheckPermission("sys:user:resetPwd")
    @PutMapping("/{id}/password/reset")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.success();
    }
}
