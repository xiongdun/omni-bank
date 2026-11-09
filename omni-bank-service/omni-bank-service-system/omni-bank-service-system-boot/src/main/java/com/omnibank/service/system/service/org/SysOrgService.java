package com.omnibank.service.system.service.org;

import com.baomidou.mybatisplus.spring.service.IService;
import com.omnibank.service.system.dto.org.OrgSaveReq;
import com.omnibank.service.system.entity.SysOrg;

import java.util.List;

/**
 * 机构/部门服务：树查询/增删改（MyBatis-Plus IService 体系）
 */
public interface SysOrgService extends IService<SysOrg> {

    /** 全量机构/部门列表（按父级+顺序排序，前端组树） */
    @Override
    List<SysOrg> list();

    /** 按 ID 查询（不存在时抛业务异常） */

    SysOrg getById(Long id);

    /** 保存机构/部门（id 空=新增，非空=修改；自动维护祖级路径） */
    void save(OrgSaveReq req);

    /** 删除机构/部门（存在下级或关联用户时拒绝） */
    void delete(Long id);
}
