package com.ruoyi.wimo.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.ruoyi.wimo.domain.WimoMailLog;

/** 验证码邮件日志 Mapper */
@Mapper
public interface WimoMailLogMapper
{
    @Insert("insert into wimo_mail_log (email, scene, code, provider, success, remark, create_time) "
            + "values (#{email}, #{scene}, #{code}, #{provider}, #{success}, #{remark}, #{createTime})")
    int insertLog(WimoMailLog log);

    @Select("<script>select id, email, scene, code, provider, success, remark, create_time as createTime from wimo_mail_log "
            + "<where>"
            + "<if test='email != null and email != \"\"'>and email like concat('%', #{email}, '%')</if>"
            + "<if test='scene != null and scene != \"\"'>and scene = #{scene}</if>"
            + "</where> order by id desc limit #{offset}, #{limit}</script>")
    List<WimoMailLog> selectList(@Param("email") String email, @Param("scene") String scene,
                                 @Param("offset") int offset, @Param("limit") int limit);

    @Select("select count(*) from wimo_mail_log")
    int countAll();

    /** 与 selectList 使用相同筛选条件，保证分页总数准确 */
    @Select("<script>select count(*) from wimo_mail_log "
            + "<where>"
            + "<if test='email != null and email != \"\"'>and email like concat('%', #{email}, '%')</if>"
            + "<if test='scene != null and scene != \"\"'>and scene = #{scene}</if>"
            + "</where></script>")
    int countList(@Param("email") String email, @Param("scene") String scene);
}
