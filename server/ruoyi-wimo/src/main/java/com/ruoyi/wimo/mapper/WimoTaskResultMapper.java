package com.ruoyi.wimo.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.ruoyi.wimo.domain.WimoTaskResult;

/** 任务结果文件 Mapper */
@Mapper
public interface WimoTaskResultMapper
{
    @Insert("insert into wimo_task_result (task_id, file_url, file_type, sort_no, create_time) "
            + "values (#{taskId}, #{fileUrl}, #{fileType}, #{sortNo}, #{createTime})")
    int insertResult(WimoTaskResult result);

    @Select("select result_id as resultId, task_id as taskId, file_url as fileUrl, file_type as fileType, sort_no as sortNo, create_time as createTime from wimo_task_result "
            + "where task_id = #{taskId} order by sort_no, result_id")
    List<WimoTaskResult> selectByTaskId(@Param("taskId") Long taskId);

    @Delete("delete from wimo_task_result where task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Long taskId);

    @Delete("<script>delete from wimo_task_result where task_id in "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteByTaskIds(@Param("ids") Long[] ids);
}
