package com.ruoyi.wimo.adapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.ruoyi.wimo.config.WimoProperties;
import com.ruoyi.wimo.exception.WimoException;

/**
 * Cloudflare R2 存储适配器（S3 兼容，纯 JDK 实现 AWS Signature V4，无需 AWS SDK）
 *
 * <p>启用：wimo.storage.provider=r2，并配置 account-id / access-key / secret-key / bucket /
 * public-domain。R2 的 SigV4 region 固定使用 auto。</p>
 *
 * @author wimo
 */
@Component
@ConditionalOnProperty(name = "wimo.storage.provider", havingValue = "r2")
public class R2Storage implements WimoStorage
{
    private static final Logger log = LoggerFactory.getLogger(R2Storage.class);
    private static final String REGION = "auto";
    private static final String SERVICE = "s3";
    private static final DateTimeFormatter AMZ = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    @Autowired private WimoProperties props;

    @Override
    public String store(byte[] data, String originalName, Long userId) throws Exception
    {
        String ak = props.getR2AccessKey(), sk = props.getR2SecretKey();
        String bucket = props.getR2Bucket(), publicDomain = props.getR2PublicDomain();
        String account = props.getR2AccountId();
        if (ak.isEmpty() || sk.isEmpty() || bucket.isEmpty() || publicDomain.isEmpty())
        {
            throw new WimoException("已启用 R2 存储但未配置 wimo.storage.r2.access-key/secret-key/bucket/public-domain");
        }

        String endpoint = (props.getR2Endpoint() == null || props.getR2Endpoint().isEmpty())
                ? "https://" + account + ".r2.cloudflarestorage.com" : props.getR2Endpoint();
        endpoint = endpoint.replaceAll("/+$", "");
        String host = URI.create(endpoint).getHost();

        String ext = OssUtil.extOf(originalName);
        String owner = "u" + (userId == null ? 0L : userId);
        String key = "wimo/" + owner + "/" + OssUtil.randomKey(ext);
        String canonicalUri = "/" + bucket + "/" + key;

        Instant now = Instant.now();
        String amzDate = AMZ.format(now);
        String dateStamp = DAY.format(now);
        String payloadHash = OssUtil.hexSha256(data);

        String canonicalHeaders = "host:" + host + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = "PUT\n" + canonicalUri + "\n\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

        String scope = dateStamp + "/" + REGION + "/" + SERVICE + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                + OssUtil.hexSha256(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] kDate = OssUtil.hmacSha256(("AWS4" + sk).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = OssUtil.hmacSha256(kDate, REGION);
        byte[] kService = OssUtil.hmacSha256(kRegion, SERVICE);
        byte[] kSigning = OssUtil.hmacSha256(kService, "aws4_request");
        String signature = OssUtil.hex(OssUtil.hmacSha256(kSigning, stringToSign));

        String authorization = "AWS4-HMAC-SHA256 Credential=" + ak + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint + canonicalUri))
                .timeout(Duration.ofSeconds(60))
                .header("Host", host)
                .header("Content-Type", "application/octet-stream")
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", amzDate)
                .header("Authorization", authorization)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300)
        {
            log.error("R2 上传失败 status={} body={}", resp.statusCode(), resp.body());
            throw new WimoException("Cloudflare R2 上传失败：" + resp.statusCode());
        }
        String url = OssUtil.joinUrl(publicDomain, key);
        log.info("R2 上传成功 key={} size={}", key, data.length);
        return url;
    }

    @Override
    public String provider() { return "r2"; }
}
