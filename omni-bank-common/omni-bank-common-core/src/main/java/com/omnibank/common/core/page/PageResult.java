package com.omnibank.common.core.page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应：接口出参不暴露 ORM 分页对象（如 MyBatis-Plus 的 IPage），
 * MP 转换见 common-mybatis 的 PageResults.from(IPage)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总页数 */
    private long pages;

    /** 当前页数据 */
    private List<T> records;

    /** 构造分页结果 */
    public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
        long pages = size > 0 ? (total + size - 1) / size : 0;
        return new PageResult<>(total, current, size, pages, records);
    }

    /** 空分页（保持请求的分页参数） */
    public static <T> PageResult<T> empty(long current, long size) {
        return of(0, current, size, Collections.emptyList());
    }
}
