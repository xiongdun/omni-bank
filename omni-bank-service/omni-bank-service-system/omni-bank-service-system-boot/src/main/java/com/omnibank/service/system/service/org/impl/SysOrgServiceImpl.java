package com.omnibank.service.system.service.org.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.system.api.enums.SysErrorCode;
import com.omnibank.service.system.dto.org.OrgSaveReq;
import com.omnibank.service.system.entity.SysOrg;
import com.omnibank.service.system.entity.SysUser;
import com.omnibank.service.system.mapper.SysOrgMapper;
import com.omnibank.service.system.mapper.SysUserMapper;
import com.omnibank.service.system.service.org.SysOrgService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 机构/部门服务实现：单表树 + 祖级路径维护。
 * TODO: 变更父级时级联刷新子树 ancestors（当前骨架仅更新自身，子树迁移场景待实现）。
 */
@Service
@RequiredArgsConstructor
public class SysOrgServiceImpl implements SysOrgService {

    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;

    @Override
    public List<SysOrg> list() {
        return orgMapper.selectList(Wrappers.<SysOrg>lambdaQuery()
                .orderByAsc(SysOrg::getParentId)
                .orderByAsc(SysOrg::getSort));
    }

    @Override
    public SysOrg getById(Long id) {
        SysOrg org = orgMapper.selectById(id);
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
            SysOrg parent = org.getParentId() == 0 ? null : orgMapper.selectById(org.getParentId());
            String parentAncestors = parent == null ? "0" : parent.getAncestors();
            org.setAncestors(parentAncestors + "," + org.getParentId());
            orgMapper.insert(org);
        } else {
            getById(org.getId());
            org.setAncestors(null);
            orgMapper.updateById(org);
        }
    }

    @Override
    public void delete(Long id) {
        getById(id);
        Long children = orgMapper.selectCount(Wrappers.<SysOrg>lambdaQuery()
                .eq(SysOrg::getParentId, id));
        if (children > 0) {
            throw new BizException(SysErrorCode.ORG_HAS_CHILDREN);
        }
        Long users = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getOrgId, id));
        if (users > 0) {
            throw new BizException(SysErrorCode.ORG_NOT_ALLOWED_DELETE);
        }
        orgMapper.deleteById(id);
    }
}
