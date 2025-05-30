package com.hmdp.utils;

import java.time.Duration;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Duration LOGIN_CODE_TTL = Duration.ofMinutes(2);
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Duration LOGIN_USER_TTL = Duration.ofMinutes(30);

    public static final Duration CACHE_NULL_TTL = Duration.ofMinutes(2);

    public static final Duration CACHE_SHOP_TTL = Duration.ofMinutes(30);
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String CACHE_TYPE_LIST_KEY = "cache:type-list";
    public static final Duration CACHE_TYPE_LIST_TTL = Duration.ofMinutes(30);

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String FOLLOW_KEY = "follow:";
    public static final String BLOG_MAILBOX = "blog:mailbox:";
}
