package com.ruoyi.wimo.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.mapper.WimoTaskMapper;
import com.ruoyi.wimo.service.IWimoTaskService;

/**
 * 套图任务 Service 实现
 *
 * <p>说明：真实生成能力接入模型服务后，把 advance() 里的模拟推进替换为
 * 查询模型任务状态即可，接口契约不变。</p>
 *
 * @author wimo
 */
@Service
public class WimoTaskServiceImpl implements IWimoTaskService
{
    /** 结果集标识（与前端 public/images/preview 下的示例图对应） */
    private static final String DEFAULT_RESULTS = "hero,shoe1,shoe2,pair1,pair2";

    /** 每张图模拟耗时（毫秒） */
    private static final long STEP_MS = 350L;

    private static final int TOTAL = 8;

    @Autowired
    private WimoTaskMapper wimoTaskMapper;

    @Override
    public WimoTask createTask(WimoTask task)
    {
        task.setStatus("1");
        task.setProgress(0);
        task.setUserId(task.getUserId() == null ? 0L : task.getUserId());
        task.setCreateTime(new Date());
        task.setUpdateTime(task.getCreateTime());
        wimoTaskMapper.insertTask(task);
        return task;
    }

    @Override
    public WimoTask getTask(Long taskId)
    {
        WimoTask task = wimoTaskMapper.selectTaskById(taskId);
        if (task == null)
        {
            return null;
        }
        if ("1".equals(task.getStatus()))
        {
            advance(task);
        }
        return task;
    }

    @Override
    public List<WimoTask> listTasks(int limit)
    {
        WimoTask query = new WimoTask();
        query.setStatus("");
        List<WimoTask> list = wimoTaskMapper.selectTaskList(query);
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    @Override
    public List<WimoTask> listTasks(WimoTask query)
    {
        return wimoTaskMapper.selectTaskList(query);
    }

    @Override
    public int deleteTasks(Long[] taskIds)
    {
        return wimoTaskMapper.deleteTaskByIds(taskIds);
    }

    /**
     * 模拟生成推进：按创建时间计算进度，跑完 8 张置为完成
     */
    private void advance(WimoTask task)
    {
        long elapsed = System.currentTimeMillis() - task.getCreateTime().getTime();
        int done = (int) Math.min(TOTAL, elapsed / STEP_MS);
        if (done >= TOTAL)
        {
            task.setStatus("2");
            task.setProgress(TOTAL);
            task.setResults(DEFAULT_RESULTS);
            task.setUpdateTime(new Date());
            wimoTaskMapper.updateTask(task);
        }
        else
        {
            task.setProgress(done);
        }
    }
}
