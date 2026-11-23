package com.ruoyi.wimo.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.domain.dto.WimoTaskSubmit;
import com.ruoyi.wimo.exception.WimoException;
import com.ruoyi.wimo.mapper.WimoTaskMapper;
import com.ruoyi.wimo.mapper.WimoTaskResultMapper;
import com.ruoyi.wimo.service.IWimoTaskService;
import com.ruoyi.wimo.service.WimoGenerateWorker;
import com.ruoyi.wimo.service.WimoSchemaService;

/** 生成任务 Service 实现 */
@Service
public class WimoTaskServiceImpl implements IWimoTaskService
{
    @Autowired
    private WimoTaskMapper taskMapper;

    @Autowired
    private WimoTaskResultMapper resultMapper;

    @Autowired
    private WimoSchemaService schemaService;

    @Autowired
    private WimoGenerateWorker worker;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public WimoTask createTask(WimoTaskSubmit submit, Long userId)
    {
        // 后端按模板校验（不信任前端的必填判断）
        schemaService.validate(submit.getTaskType(), submit);

        Map<String, Object> c = submit.getCommon() == null ? Map.of() : submit.getCommon();
        Map<String, Object> v = submit.getValues() == null ? Map.of() : submit.getValues();
        Map<String, List<String>> up = submit.getUploads() == null ? Map.of() : submit.getUploads();

        List<String> allFiles = new ArrayList<>();
        up.values().forEach(list -> { if (list != null) allFiles.addAll(list); });

        WimoTask t = new WimoTask();
        t.setUserId(userId == null ? 0L : userId);
        t.setTaskType(submit.getTaskType());
        t.setTitle(str(submit.getTitle()));
        t.setProductName(!str(v.get("storeName")).isEmpty() ? str(v.get("storeName")) : str(submit.getTitle()));
        t.setSellingPoints(str(v.get("sellingPoints")));
        t.setRatio(str(c.get("ratio")));
        t.setPlatform(str(c.get("platform")));
        t.setRegion(str(c.get("region")));
        t.setLang(str(c.get("language")));
        t.setResolution(!str(c.get("resolution")).isEmpty() ? str(c.get("resolution")) : str(c.get("videoResolution")));
        t.setQuality(str(c.get("quality")));
        t.setModel(!str(c.get("model")).isEmpty() ? str(c.get("model")) : str(c.get("videoModel")));
        t.setResultMode(str(submit.getResultMode()).isEmpty() ? "default" : submit.getResultMode());
        t.setImages(String.join(",", allFiles));
        t.setExtra(submit.getExtra());
        t.setStatus("0");
        t.setProgress(0);
        t.setResults("");
        t.setResultCount(0);
        t.setRetryCount(0);
        t.setCostMs(0L);
        t.setErrorMsg("");
        t.setCreateTime(new Date());
        t.setUpdateTime(t.getCreateTime());
        try
        {
            t.setPayload(objectMapper.writeValueAsString(submit));
        }
        catch (Exception e)
        {
            throw new WimoException("任务参数序列化失败");
        }
        taskMapper.insertTask(t);
        worker.submit(t.getTaskId());
        return t;
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
        if (t != null)
        {
            t.setResultFiles(resultMapper.selectByTaskId(taskId));
        }
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
        if (taskIds == null || taskIds.length == 0)
        {
            return 0;
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
        if (t == null)
        {
            throw new WimoException("任务不存在");
        }
        if (userId != null && userId > 0 && !userId.equals(t.getUserId()))
        {
            throw new WimoException("无权操作该任务");
        }
        return t;
    }

    private String str(Object o)
    {
        return o == null ? "" : o.toString();
    }
}
