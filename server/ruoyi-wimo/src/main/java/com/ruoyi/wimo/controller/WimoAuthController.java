package com.ruoyi.wimo.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.wimo.service.WimoAuthService;

/**
 * 用户端 · 会员账号接口（注册 / 登录 / 验证码 / 找回密码 / 资料 / 登出）
 */
@Anonymous
@RestController
@RequestMapping("/api/wimo/auth")
public class WimoAuthController extends WimoApiSupport
{
    @Autowired
    private WimoAuthService authService;

    /** 发送邮箱验证码；mock 通道会在 data.demoCode 直接返回验证码 */
    @PostMapping("/code")
    public R<Map<String, Object>> code(@RequestBody Map<String, Object> body)
    {
        return wrap(() -> authService.sendCode(s(body, "email"), s(body, "scene")));
    }

    /** 注册：email + code + password + confirmPassword + agreed */
    @PostMapping("/register")
    public R<Map<String, Object>> register(@RequestBody Map<String, Object> body)
    {
        return wrap(() -> authService.register(s(body, "email"), s(body, "code"),
                s(body, "password"), s(body, "confirmPassword"), b(body, "agreed")));
    }

    /** 密码登录 */
    @PostMapping("/login/password")
    public R<Map<String, Object>> loginByPassword(@RequestBody Map<String, Object> body)
    {
        return wrap(() -> authService.loginByPassword(s(body, "email"), s(body, "password")));
    }

    /** 验证码登录 */
    @PostMapping("/login/code")
    public R<Map<String, Object>> loginByCode(@RequestBody Map<String, Object> body)
    {
        return wrap(() -> authService.loginByCode(s(body, "email"), s(body, "code")));
    }

    /** 找回密码 */
    @PostMapping("/password/reset")
    public R<Map<String, Object>> reset(@RequestBody Map<String, Object> body)
    {
        return wrap(() -> {
            authService.resetPassword(s(body, "email"), s(body, "code"), s(body, "password"));
            return Map.of("ok", true);
        });
    }

    /** 当前登录资料 */
    @GetMapping("/profile")
    public R<Map<String, Object>> profile(
            @RequestHeader(value = "X-Wimo-Token", required = false) String wimoToken,
            @RequestHeader(value = "Authorization", required = false) String authorization)
    {
        return wrap(() -> authService.profile(token(wimoToken, authorization)));
    }

    /** 登出（使令牌立即失效） */
    @PostMapping("/logout")
    public R<Map<String, Object>> logout(
            @RequestHeader(value = "X-Wimo-Token", required = false) String wimoToken,
            @RequestHeader(value = "Authorization", required = false) String authorization)
    {
        return wrap(() -> {
            authService.logout(token(wimoToken, authorization));
            return Map.of("ok", true);
        });
    }
}
