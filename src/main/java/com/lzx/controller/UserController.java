package com.lzx.controller;

import com.lzx.dto.LoginFormDTO;
import com.lzx.dto.UserDTO;
import com.lzx.result.Result;
import com.lzx.service.UserService;
import com.lzx.utils.UserHolder;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户接口
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class UserController {

    private final UserService userService;

    /**
     * 发送验证码
     *
     * @param phone   手机号
     * @param session HttpSession
     * @return 验证码
     */
    @PostMapping("/sendCode")
    public Result<String> sendCode(String phone, HttpSession session) {
        log.info("发送验证码，phone: {}", phone);
        String code = userService.sendCode(phone, session);
        return Result.success("验证码发送成功", code);
    }

    /**
     * 登录
     *
     * @param loginForm 登录表单信息：手机号、验证码、密码
     * @return 登录成功，返回 token
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginFormDTO loginForm) {
        log.info("登录，phone: {}, code: {}", loginForm.getPhone(), loginForm.getCode());
        String token = userService.login(loginForm);
        return Result.success("登录成功", token);
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 当前登录用户信息
     */
    @GetMapping("/me")
    public Result<UserDTO> getCurrentUser() {
        UserDTO user = UserHolder.getUser();
        log.info("获取当前登录用户信息，user: {}", user);
        return Result.success("获取当前登录用户信息成功", user);
    }

    /**
     * 根据 ID 获取用户信息
     *
     * @param id 用户 ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public Result<UserDTO> queryUserById(@PathVariable Long id) {
        log.info("根据 ID 获取用户信息，ID：{}", id);
        UserDTO user = userService.queryUserById(id);
        return Result.success("获取用户信息成功", user);
    }
}
