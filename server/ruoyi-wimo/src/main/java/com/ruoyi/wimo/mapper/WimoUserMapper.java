package com.ruoyi.wimo.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.ruoyi.wimo.domain.WimoUser;

/** 会员 Mapper（含钻石钱包原子操作） */
@Mapper
public interface WimoUserMapper
{
    String COLUMNS = "user_id as userId, email, password_hash as passwordHash, nickname, status, agreed, "
            + "diamond_balance as diamondBalance, diamond_frozen as diamondFrozen, "
            + "diamond_total as diamondTotal, diamond_used as diamondUsed, "
            + "last_login_time as lastLoginTime, create_time as createTime, update_time as updateTime";

    @Insert("insert into wimo_user (email, password_hash, nickname, status, agreed, "
            + "diamond_balance, diamond_frozen, diamond_total, diamond_used, create_time, update_time) "
            + "values (#{email}, #{passwordHash}, #{nickname}, #{status}, #{agreed}, "
            + "#{diamondBalance}, 0, #{diamondTotal}, 0, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "userId")
    int insertUser(WimoUser user);

    @Select("select " + COLUMNS + " from wimo_user where email = #{email}")
    WimoUser selectByEmail(@Param("email") String email);

    @Select("select " + COLUMNS + " from wimo_user where user_id = #{userId}")
    WimoUser selectById(@Param("userId") Long userId);

    @Select("select " + COLUMNS + " from wimo_user order by user_id desc")
    List<WimoUser> selectAll();

    @Update("update wimo_user set password_hash = #{passwordHash}, update_time = #{updateTime} where user_id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash, @Param("updateTime") Date updateTime);

    @Update("update wimo_user set last_login_time = #{loginTime} where user_id = #{userId}")
    int updateLastLogin(@Param("userId") Long userId, @Param("loginTime") Date loginTime);

    @Select("select count(*) from wimo_user")
    int countAll();

    /* ---------------- 钻石钱包：全部为带条件的原子 UPDATE ---------------- */

    /** 预扣：可用余额 → 冻结。返回影响行数，0 表示余额不足 */
    @Update("update wimo_user set diamond_balance = diamond_balance - #{cost}, "
            + "diamond_frozen = diamond_frozen + #{cost}, update_time = now() "
            + "where user_id = #{userId} and diamond_balance >= #{cost}")
    int freezeDiamonds(@Param("userId") Long userId, @Param("cost") long cost);

    /** 结算：冻结 → 已消耗 */
    @Update("update wimo_user set diamond_frozen = diamond_frozen - #{cost}, "
            + "diamond_used = diamond_used + #{cost}, update_time = now() "
            + "where user_id = #{userId} and diamond_frozen >= #{cost}")
    int settleFrozen(@Param("userId") Long userId, @Param("cost") long cost);

    /** 退还：冻结 → 可用余额 */
    @Update("update wimo_user set diamond_frozen = diamond_frozen - #{cost}, "
            + "diamond_balance = diamond_balance + #{cost}, update_time = now() "
            + "where user_id = #{userId} and diamond_frozen >= #{cost}")
    int refundFrozen(@Param("userId") Long userId, @Param("cost") long cost);

    /** 管理端调整 / 充值：正数增加，负数扣减（不允许扣成负数） */
    @Update("update wimo_user set diamond_balance = diamond_balance + #{delta}, "
            + "diamond_total = diamond_total + (case when #{delta} > 0 then #{delta} else 0 end), "
            + "update_time = now() "
            + "where user_id = #{userId} and diamond_balance + #{delta} >= 0")
    int adjustDiamonds(@Param("userId") Long userId, @Param("delta") long delta);

    /** 看板：钻石总览 */
    @Select("select coalesce(sum(diamond_balance),0) as balance, "
            + "coalesce(sum(diamond_frozen),0) as frozen, "
            + "coalesce(sum(diamond_used),0) as used, "
            + "coalesce(sum(diamond_total),0) as total from wimo_user")
    Map<String, Object> diamondOverview();
}
