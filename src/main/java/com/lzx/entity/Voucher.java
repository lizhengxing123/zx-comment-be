package com.lzx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

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
@TableName("tb_voucher")
public class Voucher implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商铺id
     */
    private Long shopId;

    /**
     * 代金券标题
     */
    private String title;

    /**
     * 副标题
     */
    private String subTitle;

    /**
     * 使用规则
     */
    private String rules;

    /**
     * 支付金额，单位是分。例如200代表2元
     */
    private Long payValue;

    /**
     * 抵扣金额，单位是分。例如200代表2元
     */
    private Long actualValue;

    /**
     * 0,普通券；1,秒杀券
     */
    private Byte type;

    /**
     * 1,上架; 2,下架; 3,过期
     */
    private Byte status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 秒杀券相关信息：库存
     */
    @TableField(exist = false)
    private Integer stock;

    /**
     * 秒杀券相关信息：生效时间
     */
    @TableField(exist = false)
    private LocalDateTime beginTime;

    /**
     * 秒杀券相关信息：失效时间
     */
    @TableField(exist = false)
    private LocalDateTime endTime;
}
