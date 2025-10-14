package com.lzx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true) // 开启AspectJ动态代理，暴露代理对象
@SpringBootApplication
public class ZxdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZxdpApplication.class, args);
    }

}
