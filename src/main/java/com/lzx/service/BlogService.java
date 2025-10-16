package com.lzx.service;

import com.lzx.dto.UserDTO;
import com.lzx.entity.Blog;
import com.lzx.result.ScrollResult;

import java.util.List;

/**
 * 博客服务类接口
 */
public interface BlogService {

    /**
     * 新增博客
     *
     * @param blog 博客实体
     * @return 新增博客 ID
     */
    Long save(Blog blog);

    /**
     * 根据 ID 查询博客
     *
     * @param id 博客 ID
     * @return 博客实体
     */
    Blog getById(Long id);

    /**
     * 点赞博客，如果用户已经点赞过，则取消点赞
     *
     * @param blogId 博客 ID
     * @return 是否点赞成功
     */
    Boolean likeBlog(Long blogId);

    /**
     * 查询热门博客
     *
     * @param current 当前页码
     * @return 热门博客列表
     */
    List<Blog> queryHotBlogs(Integer current);

    /**
     * 查询博客最早点赞的 n 个人
     *
     * @param id 博客 ID
     * @return 点赞用户列表
     */
    List<UserDTO> queryBlogLikes(Long id);

    /**
     * 根据用户 ID 查询用户发布的博客
     *
     * @param current 当前页码
     * @param userId  用户 ID
     * @return 博客列表
     */
    List<Blog> queryBlogsByUserId(Integer current, Long userId);

    /**
     * 查询当前用户关注的博主发布的博客列表
     *
     * @param lastTimeStamp 上一次查询的最小时间戳
     * @param offset        偏移量
     * @return 博客列表
     */
    ScrollResult<Blog> queryBlogsOfFollow(Long lastTimeStamp, Integer offset);
}
