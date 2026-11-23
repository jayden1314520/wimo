package com.ruoyi.wimo.controller;

import java.util.function.Supplier;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.wimo.exception.WimoException;

/** 用户端接口基类：统一把业务异常转成 R.fail(msg)，保持 {code,msg,data} 契约 */
public abstract class WimoApiSupport
{
    protected <T> R<T> wrap(Supplier<T> supplier)
    {
        try
        {
            return R.ok(supplier.get());
        }
        catch (WimoException e)
        {
            return R.fail(e.getMessage());
        }
        catch (Exception e)
        {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return R.fail("服务异常：" + msg);
        }
    }

    protected String s(java.util.Map<String, Object> body, String key)
    {
        Object v = body == null ? null : body.get(key);
        return v == null ? "" : v.toString();
    }

    protected boolean b(java.util.Map<String, Object> body, String key)
    {
        Object v = body == null ? null : body.get(key);
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v));
    }

    protected String token(String authorization)
    {
        if (authorization == null)
        {
            return null;
        }
        String h = authorization.trim();
        return h.startsWith("Bearer ") ? h.substring(7).trim() : h;
    }

    /** 优先读会员专用 header，兼容 Authorization: Bearer */
    protected String token(String wimoToken, String authorization)
    {
        return (wimoToken != null && !wimoToken.trim().isEmpty()) ? wimoToken.trim() : token(authorization);
    }
}
