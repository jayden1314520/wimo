package com.ruoyi.wimo.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.redis.RedisCache;

/**
 * 会员登录令牌：UUID -> userId 存 Redis，支持服务端主动失效（登出）。
 */
@Service
public class WimoTokenService
{
    private static final String PREFIX = "wimo:token:";

    @Autowired
    private RedisCache redisCache;

    @Value("${wimo.auth.token-days:7}")
    private int tokenDays;

    public String issue(Long userId)
    {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisCache.setCacheObject(PREFIX + token, userId, tokenDays, TimeUnit.DAYS);
        return token;
    }

    public Long resolve(String token)
    {
        if (token == null || token.isEmpty())
        {
            return null;
        }
        Object val = redisCache.getCacheObject(PREFIX + token);
        if (val == null)
        {
            return null;
        }
        return Long.valueOf(val.toString());
    }

    public void revoke(String token)
    {
        if (token != null && !token.isEmpty())
        {
            redisCache.deleteObject(PREFIX + token);
        }
    }
}
