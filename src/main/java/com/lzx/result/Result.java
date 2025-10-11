package com.lzx.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果
 *
 * @param <T>
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // 当属性为null时，不序列化该属性
public class Result<T> implements Serializable {

    // true：成功、false：失败
    private Boolean success;
    // 返回信息
    private String msg;
    // 返回数据
    private T data;

    /**
     * 有数据成功
     *
     * @return 有消息，有数据的Result对象
     */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<T>(true, msg, data);
    }

    /**
     * 无数据成功
     *
     * @return 有消息，无数据的Result对象
     */
    public static <T> Result<T> success(String msg) {
        return new Result<T>(true, msg, null);
    }

    /**
     * 失败
     *
     * @return 有消息，无数据的Result对象
     */

    public static <T> Result<T> fail(String msg) {
        return new Result<T>(false, msg, null);
    }

}
