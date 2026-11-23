package com.ruoyi.wimo.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import com.ruoyi.wimo.service.WimoAuthContext;
import com.ruoyi.wimo.service.WimoTokenService;

/**
 * 会员令牌解析：有合法 token 则写入上下文，无 token 视为匿名演示（userId=0）。
 * 不做强制拦截，保证静态演示站在没有后端时也能使用。
 */
@Component
public class WimoAuthInterceptor implements HandlerInterceptor
{
    @Autowired
    private WimoTokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        if (!(handler instanceof HandlerMethod))
        {
            return true;
        }
        // 用独立 header，避免若依 JwtAuthenticationTokenFilter 误把会员 UUID 当 JWT 解析
        String h = request.getHeader("X-Wimo-Token");
        if (h == null || h.isEmpty())
        {
            String bearer = request.getHeader("Authorization");
            if (bearer != null && bearer.startsWith("Bearer "))
            {
                h = bearer.substring(7).trim();
            }
        }
        Long userId = (h == null || h.isEmpty()) ? null : tokenService.resolve(h);
        WimoAuthContext.set(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex)
    {
        WimoAuthContext.clear();
    }
}
