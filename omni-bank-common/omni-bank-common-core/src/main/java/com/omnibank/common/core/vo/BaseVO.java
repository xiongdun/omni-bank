package com.omnibank.common.core.vo;

import java.io.Serial;
import java.io.Serializable;

/**
 * VO 基类：面向前端展示的视图对象统一继承，约定 serialVersionUID。
 * 说明：实体/PO 侧统一继承 common-mybatis 的 BaseEntity（MP 实体即 PO，不重复设 BasePO）。
 */
public abstract class BaseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
