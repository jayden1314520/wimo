package com.ruoyi.wimo.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** 钻石账户流水 wimo_diamond_log */
@Data
public class WimoDiamondLog
{
    private Long id;
    private Long userId;

    /** gift/recharge/freeze/consume/refund/adjust */
    private String changeType;

    /** 带符号变动量 */
    private Long amount;

    private Long balanceBefore;
    private Long balanceAfter;
    private Long frozenBefore;
    private Long frozenAfter;

    private Long taskId;
    private String workflowKey;
    private String operator;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
