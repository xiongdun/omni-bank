package com.omnibank.service.system.controller.role;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.common.core.result.Result;
import com.omnibank.service.system.dto.role.RoleCreateReq;
import com.omnibank.service.system.dto.role.RoleQueryReq;
import com.omnibank.service.system.dto.role.RoleUpdateReq;
import com.omnibank.service.system.entity.SysRole;
import com.omnibank.service.system.service.role.SysRoleService;
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
 * 角色管理接口（菜单授权经修改接口的 menuIds 全量覆盖）
 */
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    /** 分页查询角色 */
    @SaCheckPermission("sys:role:list")
    @GetMapping("/page")
    public Result<IPage<SysRole>> page(RoleQueryReq req) {
        return Result.success(roleService.page(req));
    }

    /** 全部启用角色（下拉选择用，登录即可） */
    @GetMapping("/list")
    public Result<java.util.List<SysRole>> list() {
        return Result.success(roleService.listAll());
    }

    /** 角色详情（含数据权限） */
    @SaCheckPermission("sys:role:query")
    @GetMapping("/{id}")
    public Result<SysRole> get(@PathVariable Long id) {
        return Result.success(roleService.getById(id));
    }

    /** 新增角色（含菜单授权） */
    @SaCheckPermission("sys:role:add")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody RoleCreateReq req) {
        roleService.create(req);
        return Result.success();
    }

    /** 修改角色（menuIds 非空时覆盖菜单授权） */
    @SaCheckPermission("sys:role:edit")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody RoleUpdateReq req) {
        roleService.update(req);
        return Result.success();
    }

    /** 删除角色（已分配用户时拒绝） */
    @SaCheckPermission("sys:role:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }
}
