package com.lzx.controller;

import com.lzx.dto.UserDTO;
import com.lzx.entity.Blog;
import com.lzx.result.Result;
import com.lzx.result.ScrollResult;
import com.lzx.service.BlogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * 博客接口
 */
@Slf4j
@RestController
@RequestMapping("/blogs")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class BlogController {

    private final BlogService blogService;

    /**
     * 新增博客
     *
     * @param blog 博客实体
     * @return 新增博客 ID
     */
    @PostMapping
    public Result<Long> saveBlog(@RequestBody Blog blog) {
        log.debug("新增博客");
        Long id = blogService.save(blog);
        return Result.success("新增博客成功", id);
    }

    /**
     * 查询热门博客
     *
     * @return 热门博客列表
     */
    @GetMapping("/hot")
    public Result<List<Blog>> getHotBlogs(@RequestParam(defaultValue = "1") Integer current) {
        log.debug("查询热门博客");
        List<Blog> blogs = blogService.queryHotBlogs(current);
        return Result.success("查询热门博客成功", blogs);
    }

    /**
     * 查询博客详情
     *
     * @param id 博客 ID
     * @return 博客实体
     */
    @GetMapping("/{id}")
    public Result<Blog> getBlogDetail(@PathVariable Long id) {
        log.debug("查询博客详情");
        Blog blog = blogService.getById(id);
        return Result.success("查询博客详情成功", blog);
    }

    /**
     * 点赞博客，如果用户已经点赞过，则取消点赞
     *
     * @param id 博客 ID
     * @return 点赞结果
     */
    @PutMapping("/like/{id}")
    public Result<Void> likeBlog(@PathVariable Long id) {
        log.debug("点赞博客");
        Boolean isLiked = blogService.likeBlog(id);
        return Result.success(isLiked ? "点赞博客成功" : "取消点赞博客成功");
    }

    /**
     * 查询博客最早点赞的 n 个人
     *
     * @param id 博客 ID
     * @return 点赞用户列表
     */
    @GetMapping("/likes/{id}")
    public Result<List<UserDTO>> queryBlogLikes(@PathVariable Long id) {
        log.debug("查询博客最早点赞的n个人");
        List<UserDTO> users = blogService.queryBlogLikes(id);
        return Result.success("查询博客最早点赞的n个人成功", users);
    }

    /**
     * 根据用户 ID 查询用户发布的博客
     *
     * @param current 当前页码
     * @param userId  用户 ID
     * @return 博客列表
     */
    @GetMapping("/of/user")
    public Result<List<Blog>> queryBlogsByUserId(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam Long userId
    ) {
        log.debug("根据用户 ID 查询用户发布的博客");
        List<Blog> blogs = blogService.queryBlogsByUserId(current, userId);
        return Result.success("根据用户 ID 查询用户发布的博客成功", blogs);
    }

    /**
     * 查询当前用户关注的博主发布的博客列表
     *
     * @param lastTimeStamp 上一次查询的最小时间戳
     * @param offset        偏移量
     * @return 博客列表
     */
    @GetMapping("/of/follow")
    public Result<ScrollResult<Blog>> queryBlogsOfFollow(
            @RequestParam Long lastTimeStamp,
            @RequestParam(defaultValue = "0") Integer offset
    ) {
        log.debug("查询当前用户关注的博主发布的博客列表");
        ScrollResult<Blog> blogs = blogService.queryBlogsOfFollow(lastTimeStamp, offset);
        return Result.success("查询当前用户关注的博主发布的博客列表成功", blogs);
    }
}
