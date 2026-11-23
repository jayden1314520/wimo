package com.ruoyi.wimo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.ruoyi.wimo.interceptor.WimoAuthInterceptor;

/** 注册会员令牌解析器，仅作用于用户端 /api/wimo/** */
@Configuration
public class WimoWebMvcConfig implements WebMvcConfigurer
{
    @Autowired
    private WimoAuthInterceptor wimoAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(wimoAuthInterceptor).addPathPatterns("/api/wimo/**");
    }

    /** 允许 Nuxt 用户端跨域直连（生产把 NUXT_PUBLIC_API_BASE 指向后端域名时使用） */
    @Override
    public void addCorsMappings(CorsRegistry registry)
    {
        registry.addMapping("/api/wimo/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
