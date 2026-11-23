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

    /** 存储：local（默认，落地 RuoYi profile/upload） */
    @Value("${wimo.storage.provider:local}")
    private String storageProvider;

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
