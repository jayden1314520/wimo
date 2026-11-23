package com.ruoyi.wimo.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** 验证码邮件日志 wimo_mail_log */
@Data
public class WimoMailLog
{
    private Long id;
    private String email;
    /** register / login / reset */
    private String scene;
    private String code;
    /** mock / smtp / http */
    private String provider;
    private String success;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
