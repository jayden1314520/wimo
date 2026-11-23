package com.ruoyi.wimo.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** 功能模板配置 wimo_schema */
@Data
public class WimoSchema
{
    private Long id;
    private String taskKey;
    private String nameZh;
    private String nameEn;
    private String groupKey;
    private String icon;
    private Integer sortNo;
    /** 1 启用 0 停用 */
    private String enabled;
    /** 完整 schema JSON */
    private String schemaJson;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
