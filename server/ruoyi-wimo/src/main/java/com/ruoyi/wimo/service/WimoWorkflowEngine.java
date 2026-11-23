package com.ruoyi.wimo.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wimo.config.WimoProperties;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.domain.WimoWorkflow;
import com.ruoyi.wimo.domain.dto.WimoTaskSubmit;
import com.ruoyi.wimo.exception.WimoException;
import com.ruoyi.wimo.mapper.WimoTaskMapper;
import com.ruoyi.wimo.service.WimoBillingEngine.Quote;

/**
 * 统一工作流引擎：所有 13 个菜单的任务都从这里进入。
 *
 * <p>「一个引擎 + N 个智能体」——引擎固定 6 个阶段，智能体的能力 / 模型 / 计费 / 步骤
 * 全部由 wimo_workflow 驱动：</p>
 * <pre>
 *   1 模板校验   WimoSchemaService.validate（上传数量、必填选填）
 *   2 装载智能体 WimoWorkflowService.getByKey（含步骤、能力、模型池）
 *   3 智能计价   WimoBillingEngine.calculate（基础价 + 单位 + 清晰度/时长/模型倍率）
 *   4 钻石预扣   WimoDiamondService.freeze（余额不足直接拒绝，不留脏任务）
 *   5 任务落库   wimo_task（含计费快照）
 *   6 异步执行   WimoGenerateWorker（按智能体步骤推进，成功扣减 / 失败退还）
 * </pre>
 *
 * @author wimo
 */
@Service
public class WimoWorkflowEngine
{
    @Autowired private WimoSchemaService schemaService;
    @Autowired private WimoWorkflowService workflowService;
    @Autowired private WimoBillingEngine billingEngine;
    @Autowired private WimoDiamondService diamondService;
    @Autowired private WimoGenerateWorker worker;
    @Autowired private WimoTaskMapper taskMapper;
    @Autowired private WimoProperties props;
    @Autowired private ObjectMapper objectMapper;

    public WimoTask create(WimoTaskSubmit submit, Long userId)
    {
        // ---- 阶段 1：模板校验（不信任前端必填判断） ----
        schemaService.validate(submit.getTaskType(), submit);

        // ---- 阶段 2：装载智能体 ----
        WimoWorkflow wf = workflowService.getByKey(submit.getTaskType());
        if (wf == null) throw new WimoException("未找到智能体工作流：" + submit.getTaskType());
        if (!"1".equals(wf.getEnabled())) throw new WimoException("该智能体已停用，请稍后再试");

        // ---- 阶段 3：智能计价 ----
        Quote q = billingEngine.calculate(submit.getTaskType(), submit);

        long uid = userId == null ? 0L : userId;
        boolean charge = uid > 0 || props.isChargeAnonymous();

        // ---- 阶段 4 + 5：组装任务并落库 ----
        WimoTask t = buildTask(submit, uid, wf, q, charge);
        taskMapper.insertTask(t);

        // ---- 阶段 4（原子预扣）：放在 insert 之后以便把 taskId 写进流水 ----
        if (charge && q.cost > 0)
        {
            try
            {
                diamondService.freeze(uid, q.cost, t.getTaskId(), wf.getWorkflowKey());
            }
            catch (Exception e)
            {
                // 余额不足 / 扣费失败：删除刚落库的脏任务，保证「有任务必已扣费」
                taskMapper.deleteTaskByIds(new Long[] { t.getTaskId() });
                throw e;
            }
        }

        // ---- 阶段 6：提交异步执行（此时事务已提交，Worker 一定能读到任务） ----
        worker.submit(t.getTaskId());
        return t;
    }

    /** 智能体步骤数（Worker 据此推进进度） */
    public List<String> stepsOf(String taskType) { return workflowService.stepsOf(taskType); }

    private WimoTask buildTask(WimoTaskSubmit submit, long uid, WimoWorkflow wf, Quote q, boolean charge)
    {
        Map<String, Object> c = submit.getCommon() == null ? Map.of() : submit.getCommon();
        Map<String, Object> v = submit.getValues() == null ? Map.of() : submit.getValues();
        Map<String, List<String>> up = submit.getUploads() == null ? Map.of() : submit.getUploads();

        List<String> allFiles = new ArrayList<>();
        up.values().forEach(list -> { if (list != null) allFiles.addAll(list); });

        WimoTask t = new WimoTask();
        t.setUserId(uid);
        t.setTaskType(submit.getTaskType());
        t.setWorkflowId(wf.getWorkflowId());
        t.setTitle(str(submit.getTitle()));
        t.setProductName(!str(v.get("storeName")).isEmpty() ? str(v.get("storeName")) : str(submit.getTitle()));
        t.setSellingPoints(str(v.get("sellingPoints")));
        t.setRatio(str(c.get("ratio")));
        t.setPlatform(str(c.get("platform")));
        t.setRegion(str(c.get("region")));
        t.setLang(str(c.get("language")));
        t.setResolution(!str(c.get("resolution")).isEmpty() ? str(c.get("resolution")) : str(c.get("videoResolution")));
        t.setQuality(str(c.get("quality")));
        t.setModel(str(c.get("model")).isEmpty() ? str(c.get("videoModel")) : str(c.get("model")));
        t.setModelKey(q.modelKey);
        t.setCostDiamonds(charge ? q.cost : 0L);
        t.setDiamondState(charge && q.cost > 0 ? "0" : "9");
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
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskType", submit.getTaskType());
            payload.put("title", submit.getTitle());
            payload.put("uploads", up);
            payload.put("values", v);
            payload.put("common", c);
            payload.put("resultMode", t.getResultMode());
            Map<String, Object> wfInfo = new LinkedHashMap<>();
            wfInfo.put("workflowId", wf.getWorkflowId());
            wfInfo.put("capability", wf.getCapability());
            wfInfo.put("steps", workflowService.stepsOf(wf.getWorkflowKey()));
            payload.put("workflow", wfInfo);
            payload.put("billing", q.snapshot);
            t.setPayload(objectMapper.writeValueAsString(payload));
            t.setBillingSnapshot(objectMapper.writeValueAsString(q.snapshot));
        }
        catch (Exception e)
        {
            throw new WimoException("任务参数序列化失败");
        }
        return t;
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }
}
