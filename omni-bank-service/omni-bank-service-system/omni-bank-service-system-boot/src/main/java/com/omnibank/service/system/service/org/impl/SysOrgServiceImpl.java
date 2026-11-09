package com.omnibank.service.system.service.org.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.system.api.enums.SysErrorCode;
import com.omnibank.service.system.dto.org.OrgSaveReq;
import com.omnibank.service.system.entity.SysOrg;
import com.omnibank.service.system.entity.SysUser;
import com.omnibank.service.system.mapper.SysOrgMapper;
import com.omnibank.service.system.service.org.SysOrgService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 机构/部门服务实现（MyBatis-Plus ServiceImpl 体系，链式 lambdaQuery；用户关联经 Db 工具查询）。
 * TODO: 变更父级时级联刷新子树 ancestors（当前骨架仅更新自身，子树迁移场景待实现）。
 */
@Service
public class SysOrgServiceImpl extends ServiceImpl<SysOrgMapper, SysOrg> implements SysOrgService {

    @Override
    public List<SysOrg> list() {
        return lambdaQuery()
                .orderByAsc(SysOrg::getParentId)
                .orderByAsc(SysOrg::getSort)
                .list();
    }

    @Override
    public SysOrg getById(Long id) {
        SysOrg org = super.getById(id);
        if (org == null) {
            throw new BizException(SysErrorCode.ORG_NOT_FOUND);
        }
        return org;
    }

    @Override
    public void save(OrgSaveReq req) {
        SysOrg org = new SysOrg();
        org.setId(req.getId());
        org.setParentId(req.getParentId());
        org.setOrgName(req.getOrgName());
        org.setOrgType(req.getOrgType());
        org.setSort(req.getSort());
        org.setLeader(req.getLeader());
        org.setPhone(req.getPhone());
        org.setEmail(req.getEmail());
        org.setStatus(req.getStatus());
        if (org.getId() == null) {
            // 新增：祖级路径 = 父级祖级路径 + 父级 ID
            SysOrg parent = org.getParentId() == 0 ? null : super.getById(org.getParentId());
            String parentAncestors = parent == null ? "0" : parent.getAncestors();
            org.setAncestors(parentAncestors + "," + org.getParentId());
            saveOrUpdate(org);
        } else {
            getById(org.getId());
            org.setAncestors(null);
            updateById(org);
        }
    }

    @Override
    public void delete(Long id) {
        getById(id);
        Long children = lambdaQuery().eq(SysOrg::getParentId, id).count();
        if (children > 0) {
            throw new BizException(SysErrorCode.ORG_HAS_CHILDREN);
        }
        Long users = Db.count(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getOrgId, id));
        if (users > 0) {
            throw new BizException(SysErrorCode.ORG_NOT_ALLOWED_DELETE);
        }
        removeById(id);
    }
}
