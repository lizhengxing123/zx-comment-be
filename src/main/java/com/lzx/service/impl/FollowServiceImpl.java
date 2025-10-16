package com.lzx.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lzx.dto.UserDTO;
import com.lzx.entity.Follow;
import com.lzx.mapper.FollowMapper;
import com.lzx.mapper.UserMapper;
import com.lzx.redis.RedisConstants;
import com.lzx.service.FollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzx.service.UserService;
import com.lzx.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注服务实现类
 */
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;

    /**
     * 关注或取关用户
     *
     * @param followUserId 要关注的用户 ID
     * @param isFollow     是否关注
     */
    @Override
    public void followUser(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        // 存入关注集合的键名
        String key = RedisConstants.FOLLOW_USER_KEY + userId;
        // 关注用户 ID 作为值
        String value = followUserId.toString();
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            int isSuccess = followMapper.insert(follow);
            if (isSuccess == 1) {
                // 关注成功，放到关注集合中
                stringRedisTemplate.opsForSet().add(key, value);
            }
        } else {
            int isSuccess = followMapper.delete(
                    Wrappers.lambdaQuery(Follow.class)
                            .eq(Follow::getUserId, userId)
                            .eq(Follow::getFollowUserId, followUserId)
            );
            if (isSuccess == 1) {
                // 取关成功，从关注集合中移除
                stringRedisTemplate.opsForSet().remove(key, value);
            }
        }
    }

    /**
     * 查询当前用户是否关注了指定用户
     *
     * @param followUserId 要查询的用户 ID
     * @return 是否关注
     */
    @Override
    public Boolean isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = followMapper.selectCount(
                Wrappers.lambdaQuery(Follow.class)
                        .eq(Follow::getUserId, userId)
                        .eq(Follow::getFollowUserId, followUserId)
        );
        return count > 0;
    }

    /**
     * 查询当前用户和目标用户的共同关注列表
     *
     * @param id 目标用户 ID
     * @return 共同关注列表
     */
    @Override
    public List<UserDTO> getCommonFollows(Long id) {
        Long userId = UserHolder.getUser().getId();
        // 目标用户关注集合的键名
        String targetKey = RedisConstants.FOLLOW_USER_KEY + id;
        // 当前用户关注集合的键名
        String currentKey = RedisConstants.FOLLOW_USER_KEY + userId;
        // 取交集
        Set<String> userIds = stringRedisTemplate.opsForSet().intersect(currentKey, targetKey);
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectByIds(userIds.stream().map(Long::valueOf).collect(Collectors.toList()))
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
    }
}
