package com.lzx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

/**
 * <p>
 * 
 * </p>
 *
 * @author 李正星
 * @since 2025-09-18
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 手机号码
     */
    private String phone;

    /**
     * 密码，加密存储，不序列化
     */
    @JsonIgnore
    private String password;

    /**
     * 昵称，默认是用户id
     */
    private String nickName;

    /**
     * 人物头像
     */
    private String icon;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
