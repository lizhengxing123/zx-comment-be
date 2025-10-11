package com.lzx.dto;

import lombok.Data;

/**
 * 登录表单信息
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
