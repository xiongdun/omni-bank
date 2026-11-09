package com.omnibank.common.core.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * DTO 基类：服务间传输对象统一继承，约定 serialVersionUID
 */
public abstract class BaseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
