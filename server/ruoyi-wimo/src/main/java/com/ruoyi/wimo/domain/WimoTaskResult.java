package com.ruoyi.wimo.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** 任务结果文件 wimo_task_result */
@Data
public class WimoTaskResult
{
    private Long resultId;
    private Long taskId;
    private String fileUrl;
    private String fileType;
    private Integer sortNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
