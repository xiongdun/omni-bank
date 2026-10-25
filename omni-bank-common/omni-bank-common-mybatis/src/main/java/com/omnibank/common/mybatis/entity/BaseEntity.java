package com.omnibank.common.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 实体基类：雪花主键 + 审计字段自动填充 + 逻辑删除（del_flag：0 未删 1 已删），业务实体继承后无需重复声明公共字段
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键：雪花算法（MP 默认 ASSIGN_ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;

    /** 更新人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;

    /** 删除标志：0 未删除 1 已删除（逻辑删除） */
    @TableLogic
    private Integer delFlag;
}
