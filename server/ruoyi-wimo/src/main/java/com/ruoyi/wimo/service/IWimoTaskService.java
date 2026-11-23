package com.ruoyi.wimo.service;

import java.util.List;
import com.ruoyi.wimo.domain.WimoTask;

/**
 * 套图任务 Service
 *
 * @author wimo
 */
public interface IWimoTaskService
{
    WimoTask createTask(WimoTask task);

    WimoTask getTask(Long taskId);

    List<WimoTask> listTasks(int limit);

    List<WimoTask> listTasks(WimoTask query);

    int deleteTasks(Long[] taskIds);
}
