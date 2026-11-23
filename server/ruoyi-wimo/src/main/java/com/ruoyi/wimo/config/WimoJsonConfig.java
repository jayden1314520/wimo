package com.ruoyi.wimo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring Boot 4 不再自动暴露 ObjectMapper bean（项目 JSON 转换器用 fastjson2），
 * 这里为 wimo 模块显式提供一个，用于 schema 解析与任务载荷序列化。
 */
@Configuration
public class WimoJsonConfig
{
    @Bean
    @Primary
    public ObjectMapper wimoObjectMapper()
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
