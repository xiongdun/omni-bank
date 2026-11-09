package com.omnibank.common.mybatis.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnibank.common.core.page.PageResult;

/**
 * MyBatis-Plus 分页对象转统一分页响应：接口出参不暴露 IPage
 */
public final class PageResults {

    private PageResults() {
    }

    /** IPage → PageResult */
    public static <T> PageResult<T> from(IPage<T> page) {
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }
}
