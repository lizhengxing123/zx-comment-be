package com.lzx.utils;

import com.lzx.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户上下文，用于存储当前登录用户
 */
@Slf4j
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
