package com.ruoyi.wimo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.Data;

/**
 * 映物可插拔配置
 *
 * <p>全部带默认值，开箱即用（本地模拟）；在 application.yml 覆盖即可切换真实服务。</p>
 */
@Data
@Component
public class WimoProperties
{
    /** 邮件通道：mock（默认，写日志不真发）/ http（调用邮件 API） */
    @Value("${wimo.mail.provider:mock}")
    private String mailProvider;

    @Value("${wimo.mail.http-endpoint:}")
    private String mailHttpEndpoint;

    @Value("${wimo.mail.http-token:}")
    private String mailHttpToken;

    /** 生成器：mock（默认，本地产出示例文件）/ remote（HTTP 调用真实模型服务） */
    @Value("${wimo.generator.provider:mock}")
    private String generatorProvider;

    @Value("${wimo.generator.remote-url:}")
    private String generatorRemoteUrl;

    @Value("${wimo.generator.remote-token:}")
    private String generatorRemoteToken;

    /** 模拟生成每步耗时与结果数量 */
    @Value("${wimo.generator.step-ms:350}")
    private long generatorStepMs;

    @Value("${wimo.generator.result-count:4}")
    private int generatorResultCount;

    /** 示例素材目录（本地模拟时复制为结果文件） */
    @Value("${wimo.mock.sample-dir:/workspace/wimogen-platform/web/public/images/preview}")
    private String sampleDir;

    /** 存储：local / qiniu（七牛 Kodo） / r2（Cloudflare R2） / remote（通用网关） */
    @Value("${wimo.storage.provider:local}")
    private String storageProvider;

    @Value("${wimo.storage.remote-endpoint:}")
    private String storageRemoteEndpoint;

    @Value("${wimo.storage.remote-token:}")
    private String storageRemoteToken;

    /* ---------------- 七牛云 Kodo ---------------- */
    @Value("${wimo.storage.qiniu.access-key:}")
    private String qiniuAccessKey;

    @Value("${wimo.storage.qiniu.secret-key:}")
    private String qiniuSecretKey;

    @Value("${wimo.storage.qiniu.bucket:}")
    private String qiniuBucket;

    /** 七牛 CDN 外链域名，如 https://cdn.example.com */
    @Value("${wimo.storage.qiniu.domain:}")
    private String qiniuDomain;

    /** 上传区域 endpoint，留空按 region 自动选择 */
    @Value("${wimo.storage.qiniu.upload-url:}")
    private String qiniuUploadUrl;

    /** z0 华东 / z1 华北 / z2 华南 / na0 北美 / as0 东南亚 */
    @Value("${wimo.storage.qiniu.region:z0}")
    private String qiniuRegion;

    /* ---------------- Cloudflare R2（S3 兼容） ---------------- */
    @Value("${wimo.storage.r2.account-id:}")
    private String r2AccountId;

    @Value("${wimo.storage.r2.access-key:}")
    private String r2AccessKey;

    @Value("${wimo.storage.r2.secret-key:}")
    private String r2SecretKey;

    @Value("${wimo.storage.r2.bucket:}")
    private String r2Bucket;

    /** R2 公开访问域名（自定义域或 r2.dev 域），不带尾部斜杠 */
    @Value("${wimo.storage.r2.public-domain:}")
    private String r2PublicDomain;

    /** S3 API endpoint，留空则用 https://<account-id>.r2.cloudflarestorage.com */
    @Value("${wimo.storage.r2.endpoint:}")
    private String r2Endpoint;

    /* ---------------- 钻石 ---------------- */
    /** 注册赠送钻石，默认 100，保证新用户能体验完整链路 */
    @Value("${wimo.diamond.register-gift:100}")
    private long registerGift;

    /** 匿名演示用户（userId=0）是否计费；false 时不扣钻石，便于静态站演示 */
    @Value("${wimo.diamond.charge-anonymous:false}")
    private boolean chargeAnonymous;

    /** 验证码有效期（秒）与发送间隔（秒） */
    @Value("${wimo.auth.code-ttl:300}")
    private int codeTtl;

    @Value("${wimo.auth.code-limit-seconds:60}")
    private int codeLimitSeconds;

    /** 登录令牌有效期（天） */
    @Value("${wimo.auth.token-days:7}")
    private int tokenDays;

    /** 演示验证码：mock 模式下固定返回该码，便于无邮箱也能联调 */
    @Value("${wimo.auth.demo-code:}")
    private String demoCode;
}
