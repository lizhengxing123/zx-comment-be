package com.lzx.interceptor;

import cn.hutool.json.JSONUtil;
import com.lzx.constant.MessageConstants;
import com.lzx.dto.UserDTO;
import com.lzx.result.Result;
import com.lzx.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 登录拦截器
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("登录拦截器 preHandle 方法被调用，请求路径：{}", request.getRequestURI());

        // 1、直接从 ThreadLocal 获取用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户不存在，拦截，返回自定义 Result 信息
            log.info("用户不存在，拦截请求：{}", request.getRequestURI());
            returnResult(response, Result.fail(MessageConstants.USER_NOT_LOGIN));
            return false;
        }

        // 2、用户存在，放行
        return true;
    }

    /**
     * 返回自定义 Result 信息
     */
    private void returnResult(HttpServletResponse response, Result<String> result) throws IOException {
        // 转换为 JSON 字符串
        String json = JSONUtil.toJsonStr(result);
        // 设置响应体为 JSON 字符串
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
