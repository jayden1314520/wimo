package com.ruoyi.wimo.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.wimo.domain.dto.WimoTaskSubmit;
import com.ruoyi.wimo.service.WimoAuthContext;
import com.ruoyi.wimo.service.WimoBillingEngine;
import com.ruoyi.wimo.service.WimoCapabilityService;
import com.ruoyi.wimo.service.WimoDiamondService;
import com.ruoyi.wimo.service.WimoModelService;
import com.ruoyi.wimo.service.WimoWorkflowService;

/**
 * 用户端 · 模型目录 / 智能体目录 / 钻石预估与钱包
 *
 * <p>模型与智能体全部来自管理端配置，web 端不写死任何模型列表。</p>
 *
 * @author wimo
 */
@Anonymous
@RestController
@RequestMapping("/api/wimo")
public class WimoMarketController extends WimoApiSupport
{
    @Autowired private WimoModelService modelService;
    @Autowired private WimoWorkflowService workflowService;
    @Autowired private WimoBillingEngine billingEngine;
    @Autowired private WimoDiamondService diamondService;
    @Autowired private WimoCapabilityService capabilityService;

    /** 可选模型：按能力或智能体 key 过滤（传 workflow 时自动套用该智能体的模型白名单） */
    @GetMapping("/model/list")
    public R<Object> models(@RequestParam(required = false) String capability,
                            @RequestParam(required = false) String workflow)
    {
        return wrap(() -> modelService.publicModels(capability, workflow, workflowService));
    }

    /** 智能体目录（含起步价、计费单位），供 web 端展示「本功能怎么收费」 */
    @GetMapping("/workflow/list")
    public R<Object> workflows()
    {
        return wrap(() -> workflowService.publicWorkflows());
    }

    /**
     * 计费规则树（用户端）：5 个能力分支，每个含维度档位、模型与基础价。
     * 可传 workflow=xxx 只返回该工作流所属能力的分支，减少传输。
     */
    @GetMapping("/capability/tree")
    public R<Object> capabilityTree(@RequestParam(required = false) String workflow,
                                    @RequestParam(required = false) String capability)
    {
        return wrap(() -> {
            java.util.List<java.util.Map<String, Object>> all = capabilityService.publicTree();
            if (capability != null && !capability.isEmpty())
            {
                return all.stream().filter(m -> capability.equals(m.get("capabilityKey")))
                        .findFirst().map(m -> (Object) m).orElse(all);
            }
            if (workflow != null && !workflow.isEmpty())
            {
                com.ruoyi.wimo.domain.WimoWorkflow wf = workflowService.getByKey(workflow);
                if (wf != null)
                {
                    String ck = wf.getCapability();
                    return all.stream().filter(m -> ck.equals(m.get("capabilityKey")))
                            .findFirst().map(m -> (Object) m).orElse(all);
                }
            }
            return all;
        });
    }

    /** 提交前预估价：按当前表单参数实时算出本次消耗钻石（含逐层 breakdown 明细） */
    @PostMapping("/billing/quote")
    public R<Map<String, Object>> quote(@RequestBody WimoTaskSubmit submit)
    {
        return wrap(() -> billingEngine.quote(submit.getTaskType(), submit, WimoAuthContext.getUserId()));
    }

    /** 当前会员钻石钱包 */
    @GetMapping("/diamond/balance")
    public R<Map<String, Object>> balance()
    {
        return wrap(() -> diamondService.balanceOf(WimoAuthContext.getUserId()));
    }

    /** 当前会员钻石流水 */
    @GetMapping("/diamond/logs")
    public R<Object> myLogs(@RequestParam(defaultValue = "20") int limit)
    {
        return wrap(() -> diamondService.myLogs(WimoAuthContext.getUserId(), limit));
    }

    /** 一次性拿到页面所需全部配置，减少首屏请求 */
    @GetMapping("/market/config")
    public R<Map<String, Object>> config()
    {
        return wrap(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("workflows", workflowService.publicWorkflows());
            m.put("models", modelService.publicModels(null, null, workflowService));
            m.put("capabilityTree", capabilityService.publicTree());
            m.put("balance", diamondService.balanceOf(WimoAuthContext.getUserId()));
            return m;
        });
    }
}
