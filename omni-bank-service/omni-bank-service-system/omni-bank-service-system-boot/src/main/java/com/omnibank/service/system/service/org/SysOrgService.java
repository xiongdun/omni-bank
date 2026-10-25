package com.omnibank.service.system.service.org;

import com.omnibank.service.system.dto.org.OrgSaveReq;
import com.omnibank.service.system.entity.SysOrg;

import java.util.List;

/**
 * 机构/部门服务：树查询/增删改
 */
public interface SysOrgService {

    /** 全量机构/部门列表（前端组树） */
    List<SysOrg> list();

    /** 按 ID 查询 */
    SysOrg getById(Long id);

    /** 保存机构/部门（id 空=新增，非空=修改；自动维护祖级路径） */
    void save(OrgSaveReq req);

    /** 删除机构/部门（存在下级或关联用户时拒绝） */
    void delete(Long id);
}
