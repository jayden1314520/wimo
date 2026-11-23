package com.ruoyi.wimo.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地模拟邮件通道：不真发信，验证码写入日志表并由接口直接返回 demoCode。
 * 配置 wimo.mail.provider=http 后自动切换为 HttpMailSender。
 */
@Component
@ConditionalOnProperty(name = "wimo.mail.provider", havingValue = "mock", matchIfMissing = true)
public class MockMailSender implements WimoMailSender
{
    private static final Logger log = LoggerFactory.getLogger(MockMailSender.class);

    @Override
    public void send(String email, String scene, String code)
    {
        log.info("[MOCK MAIL] to={} scene={} code={} | 配置真实邮件通道后将发送真实邮件", email, scene, code);
    }

    @Override
    public String provider()
    {
        return "mock";
    }
}
