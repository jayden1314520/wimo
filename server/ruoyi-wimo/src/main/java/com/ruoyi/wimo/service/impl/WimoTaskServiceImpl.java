package com.ruoyi.wimo.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.domain.dto.WimoTaskSubmit;
import com.ruoyi.wimo.exception.WimoException;
import com.ruoyi.wimo.mapper.WimoTaskMapper;
import com.ruoyi.wimo.mapper.WimoTaskResultMapper;
import com.ruoyi.wimo.service.IWimoTaskService;
import com.ruoyi.wimo.service.WimoDiamondService;
import com.ruoyi.wimo.service.WimoGenerateWorker;
import com.ruoyi.wimo.service.WimoWorkflowEngine;

/** 生成任务 Service 实现：创建统一交给工作流引擎，此处负责查询与生命周期操作 */
@Service
public class WimoTaskServiceImpl implements IWimoTaskService
{
    @Autowired private WimoTaskMapper taskMapper;
    @Autowired private WimoTaskResultMapper resultMapper;
    @Autowired private WimoWorkflowEngine workflowEngine;
    @Autowired private WimoGenerateWorker worker;
    @Autowired private WimoDiamondService diamondService;

    @Override
    public WimoTask createTask(WimoTaskSubmit submit, Long userId)
    {
        // 统一入口：模板校验 → 智能体 → 计价 → 预扣 → 落库 → 异步执行
        return workflowEngine.create(submit, userId);
    }

    @Override
    public WimoTask getTask(Long taskId)
    {
        return taskMapper.selectTaskById(taskId);
    }

    @Override
    public WimoTask getTaskDetail(Long taskId)
    {
        WimoTask t = getTask(taskId);
        if (t != null) t.setResultFiles(resultMapper.selectByTaskId(taskId));
        return t;
    }

    @Override
    public List<WimoTask> listTasks(int limit)
    {
        WimoTask q = new WimoTask();
        q.setStatus("");
        List<WimoTask> list = taskMapper.selectTaskList(q);
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    @Override
    public List<WimoTask> listTasks(WimoTask query)
    {
        return taskMapper.selectTaskList(query);
    }

    @Override
    public Map<String, Object> listMyTasks(Long userId, String status, int page, int size)
    {
        int p = Math.max(1, page);
        int s = Math.min(50, Math.max(1, size));
        int total = taskMapper.countMyTasks(userId, status);
        List<WimoTask> list = taskMapper.selectMyTasks(userId, status, (p - 1) * s, s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("page", p);
        m.put("size", s);
        m.put("list", list);
        return m;
    }

    @Override
    public int deleteTasks(Long[] taskIds)
    {
        if (taskIds == null || taskIds.length == 0) return 0;
        // 删除仍在冻结中的任务时先退还钻石，避免「钱冻着、任务没了」
        for (Long id : taskIds)
        {
            WimoTask t = taskMapper.selectTaskById(id);
            if (t != null && "0".equals(t.getDiamondState())) diamondService.refund(t);
        }
        resultMapper.deleteByTaskIds(taskIds);
        return taskMapper.deleteTaskByIds(taskIds);
    }

    @Override
    public WimoTask retryTask(Long taskId, Long userId)
    {
        WimoTask t = requireOwner(taskId, userId);
        if (!"2".equals(t.getStatus()) && !"3".equals(t.getStatus()) && !"4".equals(t.getStatus()))
        {
            throw new WimoException("该任务当前状态不可重试");
        }
        // 重试不重复扣费：只有「失败/取消时已退还」的任务才需要按原账单重新预扣
        if ("2".equals(t.getDiamondState()) && t.getCostDiamonds() != null && t.getCostDiamonds() > 0)
        {
            diamondService.freeze(t.getUserId(), t.getCostDiamonds(), taskId, t.getTaskType());
            WimoTask b = new WimoTask();
            b.setTaskId(taskId);
            b.setDiamondState("0");
            b.setUpdateTime(new Date());
            taskMapper.updateTask(b);
        }
        resultMapper.deleteByTaskId(taskId);
        WimoTask reset = new WimoTask();
        reset.setTaskId(taskId);
        reset.setStatus("0");
        reset.setProgress(0);
        reset.setResults("");
        reset.setResultCount(0);
        reset.setErrorMsg("");
        reset.setRetryCount((t.getRetryCount() == null ? 0 : t.getRetryCount()) + 1);
        reset.setUpdateTime(new Date());
        taskMapper.updateTask(reset);
        worker.submit(taskId);
        return getTask(taskId);
    }

    @Override
    public WimoTask cancelTask(Long taskId, Long userId)
    {
        WimoTask t = requireOwner(taskId, userId);
        if (!"0".equals(t.getStatus()) && !"1".equals(t.getStatus()))
        {
            throw new WimoException("该任务已结束，无法取消");
        }
        WimoTask c = new WimoTask();
        c.setTaskId(taskId);
        c.setStatus("4");
        c.setUpdateTime(new Date());
        taskMapper.updateTask(c);
        // 取消即退还冻结钻石
        diamondService.refund(t);
        return getTask(taskId);
    }

    @Override
    public int deleteMine(Long taskId, Long userId)
    {
        requireOwner(taskId, userId);
        return deleteTasks(new Long[] { taskId });
    }

    private WimoTask requireOwner(Long taskId, Long userId)
    {
        WimoTask t = getTask(taskId);
        if (t == null) throw new WimoException("任务不存在");
        if (userId != null && userId > 0 && !userId.equals(t.getUserId()))
        {
            throw new WimoException("无权操作该任务");
        }
        return t;
    }
}
