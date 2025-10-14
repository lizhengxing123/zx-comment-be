package com.lzx.redis;

/**
 * 互斥锁接口
 */
public interface ILock {
    /**
     * 尝试获取锁
     *
     * @param timeoutSec 超时时间，单位秒
     * @return 是否成功获取锁
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
