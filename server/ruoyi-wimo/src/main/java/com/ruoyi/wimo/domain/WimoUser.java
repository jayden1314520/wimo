package com.ruoyi.wimo.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 映物会员对象 wimo_user
 *
 * <p>独立于若依 sys_user，避免用户端账号污染后台管理员体系。</p>
 *
 * @author wimo
 */
@Data
public class WimoUser
{
    private Long userId;

    /** 邮箱（登录账号） */
    private String email;

    /** BCrypt 密码散列（绝不明文存储） */
    private String passwordHash;

    private String nickname;

    /** 0 正常 1 停用 */
    private String status;

    /** 是否同意用户协议 */
    private String agreed;

    /** 可用钻石余额 */
    private Long diamondBalance;

    /** 生成中冻结的钻石 */
    private Long diamondFrozen;

    /** 累计获得 / 累计消耗（看板统计用） */
    private Long diamondTotal;
    private Long diamondUsed;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastLoginTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
