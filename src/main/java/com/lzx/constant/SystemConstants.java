package com.lzx.constant;

public class SystemConstants {
    // 图片上传目录
    public static final String IMAGE_UPLOAD_DIR = "D:\\study\\redis\\imgs\\";

    // 新增用户的昵称前缀
    public static final String USER_NICK_NAME_PREFIX = "user_";
    // 登录拦截器，从请求头中获取 token 键名
    public static final String HEADER_TOKEN_KEY = "authorization";

    // 最早点赞的 n 个人
    public static final int DEFAULT_PAGE_SIZE = 5;
    // 热门博客查询时，每页最多查询的博客数量
    public static final int MAX_PAGE_SIZE = 10;
}
