package com.ruoyi.wimo.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.wimo.domain.WimoTask;

/** 生成任务 Mapper */
@Mapper
public interface WimoTaskMapper
{
    int insertTask(WimoTask task);

    int updateTask(WimoTask task);

    WimoTask selectTaskById(Long taskId);

    List<WimoTask> selectTaskList(WimoTask query);

    int deleteTaskByIds(Long[] taskIds);

    /** 我的任务（分页） */
    List<WimoTask> selectMyTasks(@Param("userId") Long userId, @Param("status") String status,
                                 @Param("offset") Integer offset, @Param("limit") Integer limit);

    int countMyTasks(@Param("userId") Long userId, @Param("status") String status);

    /** 看板：状态分布 */
    List<Map<String, Object>> countByStatus();

    /** 看板：功能分布 */
    List<Map<String, Object>> countByType();

    /** 看板：近 N 天趋势 */
    List<Map<String, Object>> trendByDay(@Param("days") int days);

    /** 看板：总览指标 */
    Map<String, Object> overview();
}
