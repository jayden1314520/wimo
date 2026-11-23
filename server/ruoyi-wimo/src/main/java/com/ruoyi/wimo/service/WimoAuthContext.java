package com.ruoyi.wimo.service;

/**
 * 请求级会员上下文：由 WimoAuthInterceptor 解析 Authorization 后写入，
 * 未登录或解析失败时为 0（匿名演示），不阻断页面使用。
 */
public final class WimoAuthContext
{
    private static final ThreadLocal<Long> USER_ID = ThreadLocal.withInitial(() -> 0L);

    private WimoAuthContext() {}

    public static void set(Long userId)
    {
        USER_ID.set(userId == null ? 0L : userId);
    }

    public static Long getUserId()
    {
        return USER_ID.get();
    }

    public static void clear()
    {
        USER_ID.remove();
    }
}
