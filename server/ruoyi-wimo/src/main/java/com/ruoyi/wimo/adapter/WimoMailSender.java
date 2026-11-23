package com.ruoyi.wimo.adapter;

/** 邮件/验证码发送适配器：mock（本地演示）或 http（真实邮件 API） */
public interface WimoMailSender
{
    void send(String email, String scene, String code) throws Exception;

    String provider();
}
