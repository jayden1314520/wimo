package com.ruoyi.wimo.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * AI 模型注册表 wimo_model
 *
 * <p>管理端配置四类能力（文生图/图生图/图生视频/文生视频），用户端按能力动态拉取。</p>
 *
 * @author wimo
 */
@Data
public class WimoModel
{
    private Long modelId;

    /** 模型唯一标识，提交任务时传入 */
    private String modelKey;

    private String nameZh;
    private String nameEn;

    /** text2img / img2img / img2video / text2video */
    private String capability;

    /** 供应商：mock / remote / qiniu / ... */
    private String provider;

    private String endpoint;

    /** 密钥引用名（真实密钥放配置/环境变量，不明文落库） */
    private String credentialRef;

    /** 钻石计费倍率 */
    private BigDecimal costMultiplier;

    /** 默认参数 JSON（尺寸、时长上限、支持清晰度等） */
    private String configJson;

    /** 1 启用 0 停用 */
    private String enabled;

    private Integer sortNo;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
