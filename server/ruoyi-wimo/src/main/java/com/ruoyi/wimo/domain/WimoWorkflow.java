package com.ruoyi.wimo.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 工作流 / 智能体定义 wimo_workflow
 *
 * <p>一个菜单 = 一个智能体 = 一份计费规则 + 一个模型池 + 一组执行步骤。
 * 所有任务共用同一个引擎，差异全部由本表驱动。</p>
 *
 * @author wimo
 */
@Data
public class WimoWorkflow
{
    private Long workflowId;

    /** = wimo_schema.task_key */
    private String workflowKey;

    private String nameZh;
    private String nameEn;
    private String groupKey;

    /** 主能力，决定用户端可选模型池 */
    private String capability;

    /** 允许的模型 key，逗号分隔；空 = 该能力下全部启用模型 */
    private String allowedModels;

    private String defaultModel;

    /** 基础钻石 */
    private Long baseCost;

    /** 计费单位：count 按张 / second 按秒 / flat 固定 */
    private String unit;

    /** 每单位钻石 */
    private BigDecimal unitCost;

    /** 取单位数的字段：图片 resultCount，视频 duration */
    private String unitField;

    /** 维度倍率 JSON：{"videoResolution":{"720p":1.4},"quality":{...}} */
    private String multipliers;

    private Long minCost;
    private Long maxCost;

    /** 智能体执行步骤 JSON 数组 */
    private String stepsJson;

    private String enabled;
    private Integer sortNo;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
