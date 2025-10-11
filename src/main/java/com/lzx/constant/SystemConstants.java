package com.lzx.constant;

public class SystemConstants {
    public static final String IMAGE_UPLOAD_DIR = "D:\\lesson\\nginx-1.18.0\\html\\hmdp\\imgs\\";

    // 新增用户的昵称前缀
    public static final String USER_NICK_NAME_PREFIX = "user_";
    // 登录拦截器，从请求头中获取 token 键名
    public static final String HEADER_TOKEN_KEY = "authorization";

    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}
