package com.ruoyi.wimo.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 能力层 wimo_capability —— 钻石计费规则树的树根
 *
 * <p>一个能力分支(文生图/图生图/图生视频/文生视频/文本生成) = 一份基础计费规则。
 * 每个分支基础价、计费单位、单价、价格区间都不同。工作流可继承(costSource=inherit)
 * 或覆盖(costSource=override)这里的默认；维度档位挂在 wimo_price_node，模型挂在 wimo_model。</p>
 *
 * @author wimo
 */
@Data
public class WimoCapability
{
    private Long capabilityId;

    /** 能力唯一标识 text2text/text2img/img2img/img2video/text2video */
    private String capabilityKey;

    private String nameZh;
    private String nameEn;
    private String icon;

    /** 该能力分支的基础钻石（每一支不同） */
    private Long baseCost;

    /** 计费单位 count/second/kiloword/flat */
    private String unit;

    /** 每单位钻石 */
    private BigDecimal unitCost;

    /** 取单位数的字段 duration/resultCount/wordCount */
    private String unitField;

    private Long minCost;
    private Long maxCost;

    private String defaultModel;

    private String enabled;
    private Integer sortNo;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
