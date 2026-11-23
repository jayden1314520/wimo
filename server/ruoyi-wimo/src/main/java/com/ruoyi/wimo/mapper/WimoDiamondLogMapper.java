package com.ruoyi.wimo.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.ruoyi.wimo.domain.WimoDiamondLog;

/** 钻石流水 Mapper */
@Mapper
public interface WimoDiamondLogMapper
{
    String COLS = "id, user_id as userId, change_type as changeType, amount, "
            + "balance_before as balanceBefore, balance_after as balanceAfter, "
            + "frozen_before as frozenBefore, frozen_after as frozenAfter, "
            + "task_id as taskId, workflow_key as workflowKey, operator, remark, create_time as createTime";

    @Insert("insert into wimo_diamond_log (user_id, change_type, amount, balance_before, balance_after, "
            + "frozen_before, frozen_after, task_id, workflow_key, operator, remark, create_time) values "
            + "(#{userId}, #{changeType}, #{amount}, #{balanceBefore}, #{balanceAfter}, "
            + "#{frozenBefore}, #{frozenAfter}, #{taskId}, #{workflowKey}, #{operator}, #{remark}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertLog(WimoDiamondLog log);

    @Select("select " + COLS + " from wimo_diamond_log where user_id = #{userId} order by id desc limit #{limit}")
    List<WimoDiamondLog> selectByUser(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("select " + COLS + " from wimo_diamond_log "
            + "where (#{userId} is null or user_id = #{userId}) "
            + "and (#{changeType} is null or #{changeType} = '' or change_type = #{changeType}) "
            + "order by id desc limit #{offset}, #{limit}")
    List<WimoDiamondLog> selectPage(@Param("userId") Long userId, @Param("changeType") String changeType,
                                    @Param("offset") int offset, @Param("limit") int limit);

    @Select("select count(*) from wimo_diamond_log "
            + "where (#{userId} is null or user_id = #{userId}) "
            + "and (#{changeType} is null or #{changeType} = '' or change_type = #{changeType})")
    int countPage(@Param("userId") Long userId, @Param("changeType") String changeType);

    @Select("select count(*) from wimo_diamond_log")
    int countAll();

    /** 看板：按流水类型汇总 */
    @Select("select change_type as changeType, count(*) as cnt, coalesce(sum(amount),0) as amount "
            + "from wimo_diamond_log group by change_type")
    List<Map<String, Object>> groupByType();

    /** 看板：近 N 天消耗趋势（consume 类型） */
    @Select("select date_format(create_time, '%Y-%m-%d') as day, coalesce(sum(-amount),0) as diamonds "
            + "from wimo_diamond_log where change_type = 'consume' "
            + "and create_time >= date_sub(curdate(), interval #{days} day) "
            + "group by date_format(create_time, '%Y-%m-%d') order by day")
    List<Map<String, Object>> consumeTrend(@Param("days") int days);
}
