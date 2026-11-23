package com.ruoyi.wimo.mapper;

import java.util.Date;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.ruoyi.wimo.domain.WimoUser;

/** 会员 Mapper */
@Mapper
public interface WimoUserMapper
{
    @Insert("insert into wimo_user (email, password_hash, nickname, status, agreed, create_time, update_time) "
            + "values (#{email}, #{passwordHash}, #{nickname}, #{status}, #{agreed}, #{createTime}, #{updateTime})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "userId")
    int insertUser(WimoUser user);

    @Select("select user_id as userId, email, password_hash as passwordHash, nickname, status, agreed, "
            + "last_login_time as lastLoginTime, create_time as createTime, update_time as updateTime "
            + "from wimo_user where email = #{email}")
    WimoUser selectByEmail(@Param("email") String email);

    @Select("select user_id as userId, email, password_hash as passwordHash, nickname, status, agreed, "
            + "last_login_time as lastLoginTime, create_time as createTime, update_time as updateTime "
            + "from wimo_user where user_id = #{userId}")
    WimoUser selectById(@Param("userId") Long userId);

    @Update("update wimo_user set password_hash = #{passwordHash}, update_time = #{updateTime} where user_id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash, @Param("updateTime") Date updateTime);

    @Update("update wimo_user set last_login_time = #{loginTime} where user_id = #{userId}")
    int updateLastLogin(@Param("userId") Long userId, @Param("loginTime") Date loginTime);

    @Select("select count(*) from wimo_user")
    int countAll();
}
