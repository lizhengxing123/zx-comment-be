package com.lzx.exception;

/**
 * 基础异常类
 */
public class BaseException extends RuntimeException {
    public BaseException(String message) {
        super(message);
    }
}
