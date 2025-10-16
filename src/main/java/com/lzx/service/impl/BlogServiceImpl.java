package com.lzx.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzx.constant.SystemConstants;
import com.lzx.dto.UserDTO;
import com.lzx.entity.Blog;
import com.lzx.entity.User;
import com.lzx.mapper.BlogMapper;
import com.lzx.mapper.UserMapper;
import com.lzx.redis.RedisConstants;
import com.lzx.service.BlogService;
import com.lzx.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 博客服务类实现
 */
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class BlogServiceImpl implements BlogService {

    private final BlogMapper blogMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 保存博客
     *
     * @param blog 博客实体
     * @return 博客 ID
     */
    @Override
    public Long save(Blog blog) {
        // 设置用户 ID
        blog.setUserId(UserHolder.getUser().getId());
        blogMapper.insert(blog);
        return blog.getId();
    }

    /**
     * 查询热门博客
     *
     * @param current 当前页码
     * @return 热门博客列表
     */
    @Override
    public List<Blog> queryHotBlogs(Integer current) {
        // 查询热门博客
        List<Blog> blogs = blogMapper.selectPage(
                new Page<>(current, SystemConstants.MAX_PAGE_SIZE),
                Wrappers.lambdaQuery(Blog.class)
                        .orderByDesc(Blog::getLiked)
        ).getRecords();

        blogs.forEach(blog -> {
            // 设置用户信息
            queryBlogUser(blog);
            // 设置是否点赞
            queryBlogIsLiked(blog);
        });

        return blogs;
    }

    /**
     * 根据 ID 查询博客
     *
     * @param id 博客 ID
     * @return 博客实体
     */
    @Override
    public Blog getById(Long id) {
        // 查询博客
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
            return null;
        }
        // 查询用户信息
        queryBlogUser(blog);
        // 查询博客是否被点赞过
        queryBlogIsLiked(blog);
        return blog;
    }


    /**
     * 点赞博客，如果用户已经点赞过，则取消点赞
     *
     * @param blogId 博客 ID
     * @return 是否点赞成功
     */
    @Override
    public Boolean likeBlog(Long blogId) {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // Redis 缓存点赞用户 ID 集合键名：blog:liked:id
        String likedKey = RedisConstants.BLOG_LIKED_KEY + blogId;
        // 判断是否已点赞过
        Double score = stringRedisTemplate.opsForZSet().score(likedKey, userId.toString());
        if (score != null) {
            // 已点赞过，点赞数量减少
            int rows = updateBlogLikedCount(blogId, false);
            if (rows > 0) {
                // 数据库更新成功，更新 redis，取消点赞
                stringRedisTemplate.opsForZSet().remove(likedKey, userId.toString());
            }
            // 需要返回取消点赞，返回 false
            return BooleanUtil.isFalse(rows > 0);
        } else {
            // 没点赞过，点赞数量增加
            int rows = updateBlogLikedCount(blogId, true);
            if (rows > 0) {
                // 数据库更新成功，更新 redis，点赞
                stringRedisTemplate.opsForZSet().add(likedKey, userId.toString(), System.currentTimeMillis());
            }
            // 需要返回点赞成功，返回 true
            return BooleanUtil.isTrue(rows > 0);
        }
    }

    /**
     * 查询博客最早点赞的 n 个人
     *
     * @param id 博客 ID
     * @return 点赞用户列表
     */
    @Override
    public List<UserDTO> queryBlogLikes(Long id) {
        // Redis 缓存点赞用户 ID 集合键名：blog:liked:id
        String likedKey = RedisConstants.BLOG_LIKED_KEY + id;
        // 查询点赞用户 ID 集合
        Set<String> userSet = stringRedisTemplate.opsForZSet().range(likedKey, 0, SystemConstants.DEFAULT_PAGE_SIZE - 1);
        if (userSet == null || userSet.isEmpty()) {
            return List.of();
        }
        // 将 set 集合转变为有序 list
        List<String> userIds = userSet.stream().toList();

        // 查询点赞用户信息，查询的时候需要按照传递的顺序返回
        // SELECT id, nick_name, icon FROM tb_user WHERE id IN (userIds) ORDER BY FIELD(id, userIds)
        return userMapper.selectList(
                        Wrappers.lambdaQuery(User.class)
                                .select(User::getId, User::getNickName, User::getIcon)
                                .in(User::getId, userIds)
                                .last("ORDER BY FIELD(id, " + StrUtil.join(",", userIds) + ")")
                )
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
    }


    // ------------------------- 私有方法 -------------------------

    /**
     * 查询博客用户信息
     *
     * @param blog 博客实体
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userMapper.selectById(userId);
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
    }

    /**
     * 修改博客点赞数量
     *
     * @param blogId  博客 ID
     * @param isLiked 是否点赞
     * @return 影响行数
     */
    private Integer updateBlogLikedCount(Long blogId, Boolean isLiked) {
        // {0} 会被 isLiked 参数值替换，不能使用 ? 占位符
        return blogMapper.update(
                null,
                Wrappers.lambdaUpdate(Blog.class)
                        .setSql("liked = liked + (CASE WHEN {0} THEN 1 ELSE -1 END)", isLiked)
                        .eq(Blog::getId, blogId)
        );
    }

    /**
     * 查询博客是否被点赞过
     *
     * @param blog 博客实体
     */
    private void queryBlogIsLiked(Blog blog) {
        // 获取当前登录用户
        UserDTO userDTO = UserHolder.getUser();
        if(userDTO == null){
            // 用户未登录，默认未点赞
            blog.setIsLiked(false);
            return;
        }
        Long userId = userDTO.getId();
        String likedKey = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        blog.setIsLiked(stringRedisTemplate.opsForZSet().score(likedKey, userId) != null);
    }
}
