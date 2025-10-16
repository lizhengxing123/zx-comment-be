package com.lzx.controller;

import com.lzx.dto.UserDTO;
import com.lzx.result.Result;
import com.lzx.service.FollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Set;

/**
 * 关注接口
 */
@Slf4j
@RestController
@RequestMapping("/follows")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class FollowController {

    private final FollowService followService;

    /**
     * 关注或取关用户
     *
     * @param followUserId 要关注的用户 ID
     * @param isFollow     是否关注
     * @return 结果
     */
    @PutMapping("/{followUserId}/{isFollow}")
    public Result<Void> followUser(
            @PathVariable("followUserId") Long followUserId,
            @PathVariable("isFollow") Boolean isFollow
    ) {
        log.info("关注或取关用户，followUserId={}, isFollow={}", followUserId, isFollow);
        followService.followUser(followUserId, isFollow);
        return Result.success("操作成功");
    }

    /**
     * 查询当前用户是否关注了指定用户
     *
     * @param followUserId 要查询的用户 ID
     * @return 是否关注
     */
    @GetMapping("/or/not/{followUserId}")
    public Result<Boolean> isFollow(
            @PathVariable("followUserId") Long followUserId
    ) {
        log.info("查询当前用户是否关注了指定用户，followUserId={}", followUserId);
        Boolean isFollow = followService.isFollow(followUserId);
        return Result.success("查询成功", isFollow);
    }

    /**
     * 查询当前用户和目标用户的共同关注列表
     *
     * @param id 目标用户 ID
     * @return 共同关注列表
     */
    @GetMapping("/common/{id}")
    public Result<List<UserDTO>> getCommonFollows(@PathVariable("id") Long id) {
        log.info("查询当前用户和目标用户的共同关注列表，id={}", id);
        List<UserDTO> users = followService.getCommonFollows(id);
        return Result.success("查询成功", users);
    }
}
