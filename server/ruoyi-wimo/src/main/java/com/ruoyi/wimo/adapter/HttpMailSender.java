package com.ruoyi.wimo.adapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.ruoyi.wimo.config.WimoProperties;
import com.ruoyi.wimo.exception.WimoException;

/**
 * 真实邮件通道：把 {to, scene, code, subject, text} POST 到可配置邮件 API。
 * 适用于 Resend / SendGrid / 自建邮件网关；只要接口返回 2xx 即视为成功。
 *
 * <pre>
 * wimo:
 *   mail:
 *     provider: http
 *     http-endpoint: https://api.example.com/mail/send
 *     http-token: your-api-token
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "wimo.mail.provider", havingValue = "http")
public class HttpMailSender implements WimoMailSender
{
    private static final Logger log = LoggerFactory.getLogger(HttpMailSender.class);

    @Autowired
    private WimoProperties props;

    @Override
    public void send(String email, String scene, String code) throws Exception
    {
        String endpoint = props.getMailHttpEndpoint();
        if (endpoint == null || endpoint.isEmpty())
        {
            throw new WimoException("已启用真实邮件通道但未配置 wimo.mail.http-endpoint");
        }
        String sceneName = "register".equals(scene) ? "注册" : "login".equals(scene) ? "登录" : "找回密码";
        String body = "{"
                + "\"to\":\"" + esc(email) + "\","
                + "\"scene\":\"" + scene + "\","
                + "\"subject\":\"映物 VIVID " + sceneName + "验证码\","
                + "\"text\":\"您的" + sceneName + "验证码是 " + code + "，5 分钟内有效。\","
                + "\"code\":\"" + code + "\"}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getMailHttpToken())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300)
        {
            throw new WimoException("邮件 API 返回 " + resp.statusCode());
        }
        log.info("验证码邮件已发送 to={} scene={}", email, scene);
    }

    @Override
    public String provider()
    {
        return "http";
    }

    private String esc(String s)
    {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
