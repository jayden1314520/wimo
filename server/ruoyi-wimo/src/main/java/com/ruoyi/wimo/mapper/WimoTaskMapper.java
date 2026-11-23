package com.ruoyi.wimo.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.wimo.domain.WimoTask;

/**
 * 套图任务 Mapper
 *
 * @author wimo
 */
@Mapper
public interface WimoTaskMapper
{
    int insertTask(WimoTask task);

    int updateTask(WimoTask task);

    WimoTask selectTaskById(Long taskId);

    List<WimoTask> selectTaskList(WimoTask query);

    int deleteTaskByIds(Long[] taskIds);
}
