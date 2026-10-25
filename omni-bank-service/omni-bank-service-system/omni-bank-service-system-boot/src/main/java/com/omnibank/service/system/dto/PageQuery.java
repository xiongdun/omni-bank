package com.omnibank.service.system.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询基类：current 从 1 开始
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码（从 1 开始） */
    private long current = 1;

    /** 每页条数（默认 10，上限 100） */
    private long size = 10;

    /** 防御性上限 */
    public long getSize() {
        return Math.min(size, 100);
    }
}
