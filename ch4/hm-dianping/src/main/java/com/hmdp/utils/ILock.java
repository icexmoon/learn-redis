package com.hmdp.utils;

/**
 * Created with IntelliJ IDEA.
 *
 * @author : 魔芋红茶
 * @version : 1.0
 * @Project : hm-dianping
 * @Package : com.hmdp.utils
 * @ClassName : .java
 * @createTime : 2025/5/3 15:03
 * @Email : icexmoon@qq.com
 * @Website : https://icexmoon.cn
 * @Description : 分布式锁
 */
public interface ILock {
    /**
     * 尝试获取锁(非阻塞式)
     *
     * @param timeoutSec 超时自动释放（单位，秒）
     * @return
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
