package com.omnibank.service.agent.dd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnibank.service.agent.dd.entity.DdLoanApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * 授信申请
 */
@Mapper
public interface DdLoanApplicationMapper extends BaseMapper<DdLoanApplication> {
}
