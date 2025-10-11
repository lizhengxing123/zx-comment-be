package com.lzx.handler;

import com.lzx.exception.BaseException;
import com.lzx.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex 业务异常
     * @return Result 异常信息
     */
    @ExceptionHandler(BaseException.class)
    public Result<String> exceptionHandler(BaseException ex){
        log.error("业务异常信息：{}", ex.getMessage());
        return Result.fail(ex.getMessage());
    }

    /**
     * 捕获运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<String> exceptionHandler(RuntimeException ex){
        log.error("运行时异常信息：{}", ex.getMessage());
        ex.printStackTrace();
        return Result.fail(ex.getMessage() == null ? "运行时异常" : ex.getMessage());
    }

}
