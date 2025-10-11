package com.lzx.service;

import com.lzx.dto.LoginFormDTO;
import com.lzx.dto.UserDTO;
import com.lzx.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpSession;

/**
 * 用户服务类
 */
public interface UserService {

    /**
     * 发送验证码
     *
     * @param phone 手机号
     * @param session HttpSession
     * @return 验证码
     */
    String sendCode(String phone, HttpSession session);

     /**
     * 登录
     *
     * @param loginForm 登录表单
      * @return token 登录凭证
     */
    String login(LoginFormDTO loginForm);
}
