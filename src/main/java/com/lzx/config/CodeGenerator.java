package com.lzx.config;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;

import java.nio.file.Paths;

/**
 * mybatis-plus 代码生成器
 */
public class CodeGenerator {
    public static void main(String[] args) {
        FastAutoGenerator.create("jdbc:mysql://localhost:3306/zxdp", "root", "123456")
                // 全局配置
                .globalConfig(builder -> builder
                        .author("李正星")
                        .outputDir(Paths.get(System.getProperty("user.dir")) + "/zxdp/src/main/java")
                        .commentDate("yyyy-MM-dd")
                        .disableOpenDir()
                )
                // 包配置
                .packageConfig(builder -> builder
                        .parent("com.lzx")
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .xml("mapper.xml")
                )
                // 策略配置
                .strategyConfig(builder -> builder
                        // 移除表前缀，需要放前面
                        .addTablePrefix("tb_")
                        // 去掉默认的"I"前缀
                        .serviceBuilder().formatServiceFileName("%sService")
                        .entityBuilder()
                        .enableLombok()
                        .enableFileOverride()
                )
                .execute();
    }
}
