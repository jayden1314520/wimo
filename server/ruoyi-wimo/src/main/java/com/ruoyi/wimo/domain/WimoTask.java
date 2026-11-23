package com.ruoyi.wimo.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * AI 套图任务对象 wimo_task
 *
 * @author wimo
 */
@Data
public class WimoTask
{
    /** 任务ID */
    private Long taskId;

    /** 用户ID（未登录为 0） */
    private Long userId;

    /** 商品名称 */
    private String productName;

    /** 卖点与要求 */
    private String sellingPoints;

    /** 画面比例 */
    private String ratio;

    /** 平台 */
    private String platform;

    /** 国家/地区 */
    private String region;

    /** 语言 */
    private String lang;

    /** 清晰度 */
    private String resolution;

    /** 质量 */
    private String quality;

    /** 模型 */
    private String model;

    /** 结果配置（default/custom） */
    private String resultMode;

    /** 素材列表（逗号分隔，最多 6 个） */
    private String images;

    /** 状态（0待生成 1生成中 2完成 3失败） */
    private String status;

    /** 进度（0-8） */
    private Integer progress;

    /** 结果图（逗号分隔的图片标识） */
    private String results;

    /** 失败原因 */
    private String errorMsg;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
