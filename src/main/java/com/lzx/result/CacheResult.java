package com.lzx.result;

public class CacheResult<T> {
    private final boolean exists;  // 缓存是否存在
    private final T data;          // 缓存数据

    private CacheResult(boolean exists, T data) {
        this.exists = exists;
        this.data = data;
    }

    // 缓存命中
    public static <T> CacheResult<T> hit(T data) {
        return new CacheResult<>(true, data);
    }

    // 空值缓存
    public static <T> CacheResult<T> nullValue() {
        return new CacheResult<>(true, null);
    }

    // 缓存未命中
    public static <T> CacheResult<T> miss() {
        return new CacheResult<>(false, null);
    }

    public boolean isExists() {
        return exists;
    }

    public T getData() {
        return data;
    }
}
