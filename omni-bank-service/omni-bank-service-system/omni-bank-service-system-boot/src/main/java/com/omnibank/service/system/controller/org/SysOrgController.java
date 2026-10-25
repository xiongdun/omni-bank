package com.omnibank.service.system.controller.org;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.omnibank.common.core.result.Result;
import com.omnibank.service.system.dto.org.OrgSaveReq;
import com.omnibank.service.system.entity.SysOrg;
import com.omnibank.service.system.service.org.SysOrgService;
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
 * 机构/部门管理接口（平铺列表，前端组树）
 */
@RestController
@RequestMapping("/org")
@RequiredArgsConstructor
public class SysOrgController {

    private final SysOrgService orgService;

    /** 全量机构/部门列表 */
    @SaCheckPermission("sys:org:list")
    @GetMapping("/list")
    public Result<List<SysOrg>> list() {
        return Result.success(orgService.list());
    }

    /** 机构/部门详情 */
    @SaCheckPermission("sys:org:list")
    @GetMapping("/{id}")
    public Result<SysOrg> get(@PathVariable Long id) {
        return Result.success(orgService.getById(id));
    }

    /** 保存机构/部门（id 空=新增，非空=修改） */
    @SaCheckPermission("sys:org:add")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody OrgSaveReq req) {
        orgService.save(req);
        return Result.success();
    }

    /** 删除机构/部门（存在下级或关联用户时拒绝） */
    @SaCheckPermission("sys:org:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return Result.success();
    }
}
