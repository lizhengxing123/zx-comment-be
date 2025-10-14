package com.lzx.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.lzx.redis.RedisConstants;
import com.lzx.constant.SystemConstants;
import com.lzx.dto.UserDTO;
import com.lzx.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 刷新登录状态拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("刷新登录状态拦截器 preHandle 方法被调用，请求路径：{}", request.getRequestURI());

        // 1、获取请求头中的 token
        String token = request.getHeader(SystemConstants.HEADER_TOKEN_KEY);
        if (StrUtil.isBlank(token)) {
            // token 直接放行
            log.info("token 不存在，直接放行请求：{}", request.getRequestURI());
            return true;
        }

        // 2、从 redis 中获取用户
        // 2.1 构建 redis 中的 key
        String redisKey = RedisConstants.LOGIN_USER_KEY + token;

        // 2.2 从 redis 中获取用户信息
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(redisKey);
        if (entries.isEmpty()) {
            // 用户不存在，直接放行
            log.info("用户不存在，直接放行请求：{}", request.getRequestURI());
            return true;
        }

        // 3、转换为 UserDTO 对象
        UserDTO user = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
        // 保存到 ThreadLocal
        log.info("用户存在，用户信息：{}", user);
        UserHolder.saveUser(user);

        // 4、刷新 token 过期时间
        log.info("刷新 token 过期时间");
        // TODO 后面需要改为分钟
        stringRedisTemplate.expire(redisKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.DAYS);

        // 5、校验通过，放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除 ThreadLocal 中的用户
        UserHolder.removeUser();
    }
}
