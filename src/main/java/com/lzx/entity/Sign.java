package com.lzx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author 李正星
 * @since 2025-09-18
 */
@Getter
@Setter
@TableName("tb_sign")
public class Sign implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 签到的年
     */
    private LocalDate year;

    /**
     * 签到的月
     */
    private Byte month;

    /**
     * 签到的日期
     */
    private LocalDate date;

    /**
     * 是否补签
     */
    private Byte isBackup;
}
