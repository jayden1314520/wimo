package com.ruoyi.wimo.domain;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * AI 生成任务对象 wimo_task
 *
 * <p>扁平字段保留用于列表检索与兼容旧接口，完整参数存 payload JSON，
 * 结果文件逐条存 wimo_task_result。</p>
 *
 * @author wimo
 */
@Data
public class WimoTask
{
    private Long taskId;

    /** 会员ID（0 = 匿名演示） */
    private Long userId;

    /** 功能 key，对应 wimo_schema.task_key */
    private String taskType;

    /** 关联工作流 / 智能体 */
    private Long workflowId;

    private String title;

    private String productName;

    private String sellingPoints;

    private String ratio;
    private String platform;
    private String region;
    private String lang;
    private String resolution;
    private String quality;
    private String model;

    /** 计费采用的模型 key（快照） */
    private String modelKey;

    /** 本单消耗钻石 */
    private Long costDiamonds;

    /** 计费明细快照 JSON（规则改动不影响历史账单） */
    private String billingSnapshot;

    /** 0 已冻结待结算 / 1 已扣减 / 2 已退还 / 9 不计费（匿名演示） */
    private String diamondState;

    private String resultMode;

    /** 素材 URL 列表（逗号分隔） */
    private String images;

    /** 完整参数 JSON（uploads/values/common） */
    private String payload;

    /** 0待生成 1生成中 2完成 3失败 4已取消 */
    private String status;

    /** 进度 0-100 */
    private Integer progress;

    /** 结果 URL（逗号分隔，兼容前端 previewUrl） */
    private String results;

    /** 扩展参数（兼容旧字段） */
    private String extra;

    private Integer resultCount;
    private Integer retryCount;
    private Long costMs;
    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 结果文件明细（非持久化，查询详情时填充） */
    private List<WimoTaskResult> resultFiles;
}
