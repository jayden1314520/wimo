package com.ruoyi.wimo.adapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.ruoyi.wimo.config.WimoProperties;
import com.ruoyi.wimo.exception.WimoException;

/**
 * 七牛云 Kodo 存储适配器（纯 JDK 实现上传凭证签名 + 表单上传，无需引入七牛 SDK）
 *
 * <p>启用：wimo.storage.provider=qiniu，并配置 access-key / secret-key / bucket / domain。</p>
 * <p>上传凭证：Base64URL(putPolicy) → HMAC-SHA1 → AccessKey:Sign:EncodedPolicy，
 * POST multipart 到区域上传域名，成功后返回 CDN 外链。</p>
 *
 * @author wimo
 */
@Component
@ConditionalOnProperty(name = "wimo.storage.provider", havingValue = "qiniu")
public class QiniuStorage implements WimoStorage
{
    private static final Logger log = LoggerFactory.getLogger(QiniuStorage.class);

    @Autowired private WimoProperties props;

    @Override
    public String store(byte[] data, String originalName, Long userId) throws Exception
    {
        String ak = props.getQiniuAccessKey(), sk = props.getQiniuSecretKey();
        String bucket = props.getQiniuBucket(), domain = props.getQiniuDomain();
        if (ak.isEmpty() || sk.isEmpty() || bucket.isEmpty())
        {
            throw new WimoException("已启用七牛存储但未配置 wimo.storage.qiniu.access-key/secret-key/bucket");
        }
        if (domain.isEmpty())
        {
            throw new WimoException("七牛存储需配置 wimo.storage.qiniu.domain（CDN 外链域名）");
        }

        String ext = OssUtil.extOf(originalName);
        String owner = "u" + (userId == null ? 0L : userId);
        String key = "wimo/" + owner + "/" + OssUtil.randomKey(ext);

        long deadline = System.currentTimeMillis() / 1000 + 3600;
        String policy = "{\"scope\":\"" + bucket + ":" + key + "\",\"deadline\":" + deadline + "}";
        String encodedPolicy = OssUtil.base64Url(policy);
        String sign = OssUtil.base64Url(OssUtil.hmacSha1(sk, encodedPolicy.getBytes(StandardCharsets.UTF_8)));
        String token = ak + ":" + sign + ":" + encodedPolicy;

        String boundary = "----wimo" + System.nanoTime();
        byte[] body = OssUtil.multipart(boundary, token, key, "file" + ext, data);

        HttpRequest req = HttpRequest.newBuilder(URI.create(uploadUrl()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300)
        {
            log.error("七牛上传失败 status={} body={}", resp.statusCode(), resp.body());
            throw new WimoException("七牛上传失败：" + resp.statusCode());
        }
        String url = OssUtil.joinUrl(domain, key);
        log.info("七牛上传成功 key={} size={}", key, data.length);
        return url;
    }

    private String uploadUrl()
    {
        String custom = props.getQiniuUploadUrl();
        if (custom != null && !custom.isEmpty()) return custom;
        String region = props.getQiniuRegion() == null ? "z0" : props.getQiniuRegion();
        return switch (region)
        {
            case "z1" -> "https://upload-z1.qiniup.com/";
            case "z2" -> "https://upload-z2.qiniup.com/";
            case "na0" -> "https://upload-na0.qiniup.com/";
            case "as0" -> "https://upload-as0.qiniup.com/";
            default -> "https://upload.qiniup.com/";
        };
    }

    @Override
    public String provider() { return "qiniu"; }
}
