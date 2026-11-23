package com.ruoyi.wimo.adapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.ruoyi.wimo.exception.WimoException;

/**
 * 对象存储网关适配器：把文件二进制 PUT 到可配置 endpoint，
 * 网关返回可访问 URL（纯文本或 JSON {"url":"..."}）。
 * 配置 wimo.storage.provider=remote 且 wimo.storage.remote-endpoint 后启用。
 */
@Component
@ConditionalOnProperty(name = "wimo.storage.provider", havingValue = "remote")
public class RemoteStorage implements WimoStorage
{
    @Value("${wimo.storage.remote-endpoint:}")
    private String endpoint;

    @Value("${wimo.storage.remote-token:}")
    private String token;

    @Override
    public String store(byte[] data, String originalName, Long userId) throws Exception
    {
        if (endpoint == null || endpoint.isEmpty())
        {
            throw new WimoException("已启用对象存储但未配置 wimo.storage.remote-endpoint");
        }
        String ext = ".jpg";
        if (originalName != null && originalName.contains("."))
        {
            ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint + "?name=" + name))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300)
        {
            throw new WimoException("对象存储返回 " + resp.statusCode());
        }
        String body = resp.body();
        if (body != null && body.contains("\"url\""))
        {
            int i = body.indexOf("\"url\"");
            int s = body.indexOf('"', i + 6);
            int e = body.indexOf('"', s + 1);
            if (s > 0 && e > s)
            {
                return body.substring(s + 1, e).replace("\\/", "/");
            }
        }
        return body == null || body.isBlank() ? endpoint + "/" + name : body.trim();
    }

    @Override
    public String provider()
    {
        return "remote";
    }
}
