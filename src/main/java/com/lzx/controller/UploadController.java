package com.lzx.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.lzx.constant.SystemConstants;
import com.lzx.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 文件上传接口
 */
@Slf4j
@RestController
@RequestMapping("/uploads")
public class UploadController {

    /**
     * 上传博客图片
     *
     * @param image 图片文件
     * @return 图片访问路径
     */
    @PostMapping("/blog")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.success("文件上传成功", fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 删除博客图片
     *
     * @param filename 图片文件名
     * @return 删除结果
     */
    @DeleteMapping("/blog/delete")
    public Result<Void> deleteBlogImg(@RequestParam("name") String filename) {
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.success("文件删除成功");
    }

    /**
     * 生成新文件名
     *
     * @param originalFilename 原始文件名
     * @return 新文件名
     */
    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 使用 UUID 生成随机文件名
        String name = UUID.randomUUID().toString();
        // 计算 UUID 字符串的哈希码（一个整数）
        int hash = name.hashCode();
        // 计算一级目录，0xF 是十六进制，对应二进制的 1111，与运算（&）的结果会得到哈希码的最后 4 位，范围是 0-15
        int d1 = hash & 0xF;
        // 计算二级目录，右移 4 位，再与 0xF 进行与运算，得到哈希码的第 5-8 位，范围是 0-15
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
