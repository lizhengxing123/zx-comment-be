package com.lzx.config;

import com.lzx.interceptor.LoginInterceptor;
import com.lzx.interceptor.RefreshTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Mvc 配置类
 */
@Configuration
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class MvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final RefreshTokenInterceptor refreshTokenInterceptor;

    /**
     * 配置拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 刷新登录状态拦截器, 对所有请求都生效
        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")
                .order(0);
        // 登录拦截器
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/users/login",
                        "/users/sendCode",
                        "/shopTypes/list",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/**"
                )
                .order(1);

    }
}
