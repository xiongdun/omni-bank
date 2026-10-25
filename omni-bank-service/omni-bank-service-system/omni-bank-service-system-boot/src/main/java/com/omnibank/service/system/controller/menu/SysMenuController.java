package com.omnibank.service.system.controller.menu;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.omnibank.common.core.result.Result;
import com.omnibank.service.system.dto.menu.MenuSaveReq;
import com.omnibank.service.system.entity.SysMenu;
import com.omnibank.service.system.service.menu.SysMenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单/权限点管理接口（平铺列表，前端组树）
 */
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    /** 全量菜单/权限点列表 */
    @SaCheckPermission("sys:menu:list")
    @GetMapping("/list")
    public Result<List<SysMenu>> list() {
        return Result.success(menuService.list());
    }

    /** 保存菜单（id 空=新增，非空=修改） */
    @SaCheckPermission("sys:menu:add")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody MenuSaveReq req) {
        menuService.save(req);
        return Result.success();
    }

    /** 删除菜单（存在下级时拒绝） */
    @SaCheckPermission("sys:menu:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }
}
