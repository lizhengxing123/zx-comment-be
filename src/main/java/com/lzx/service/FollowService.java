package com.lzx.service;

import com.lzx.dto.UserDTO;
import com.lzx.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 关注服务接口
 */
public interface FollowService {

    /**
     * 关注或取关用户
     *
     * @param followUserId 要关注的用户 ID
     * @param isFollow     是否关注
     */
    void followUser(Long followUserId, Boolean isFollow);

    /**
     * 查询当前用户是否关注了指定用户
     *
     * @param followUserId 要查询的用户 ID
     * @return 是否关注
     */
    Boolean isFollow(Long followUserId);

    /**
     * 查询当前用户和目标用户的共同关注列表
     *
     * @param id 目标用户 ID
     * @return 共同关注列表
     */
    List<UserDTO> getCommonFollows(Long id);
}
