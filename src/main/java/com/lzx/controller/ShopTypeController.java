package com.lzx.controller;

import com.lzx.entity.ShopType;
import com.lzx.result.Result;
import com.lzx.service.ShopTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 店铺类型接口
 */
@Slf4j
@RestController
@RequestMapping("/shopTypes")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ShopTypeController {

    private final ShopTypeService shopTypeService;

    /**
     * 获取所有店铺类型列表
     *
     * @return 所有店铺类型实体列表
     */
    @GetMapping("/list")
    public Result<List<ShopType>> list() {
        log.info("获取所有店铺类型列表");
        return Result.success("获取所有店铺类型列表成功", shopTypeService.getList());
    }
}
