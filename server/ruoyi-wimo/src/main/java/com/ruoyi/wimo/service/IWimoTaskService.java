package com.ruoyi.wimo.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.domain.dto.WimoTaskSubmit;

/** 生成任务 Service（schema 驱动 + 异步生成 + 记录管理） */
public interface IWimoTaskService
{
    WimoTask createTask(WimoTaskSubmit submit, Long userId);

    WimoTask getTask(Long taskId);

    /** 详情：附带结果文件明细 */
    WimoTask getTaskDetail(Long taskId);

    List<WimoTask> listTasks(int limit);

    List<WimoTask> listTasks(WimoTask query);

    /** 我的任务分页 */
    Map<String, Object> listMyTasks(Long userId, String status, int page, int size);

    int deleteTasks(Long[] taskIds);

    WimoTask retryTask(Long taskId, Long userId);

    WimoTask cancelTask(Long taskId, Long userId);

    int deleteMine(Long taskId, Long userId);
}
