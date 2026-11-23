package com.ruoyi.wimo.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.wimo.domain.WimoWorkflow;
import com.ruoyi.wimo.service.WimoModelService;
import com.ruoyi.wimo.service.WimoWorkflowService;

/** 管理端 · 智能体工作流（13 个菜单的计费规则、模型池与执行步骤） */
@RestController
@RequestMapping("/wimo/workflow")
public class WimoWorkflowAdminController extends BaseController
{
    @Autowired private WimoWorkflowService workflowService;
    @Autowired private WimoModelService modelService;

    @PreAuthorize("@ss.hasPermi('wimo:workflow:list')")
    @GetMapping("/list")
    public AjaxResult list()
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", workflowService.listAll());
        m.put("total", workflowService.listAll().size());
        m.put("models", modelService.listAll());
        return success(m);
    }

    @PreAuthorize("@ss.hasPermi('wimo:workflow:list')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) { return success(workflowService.getById(id)); }

    @PreAuthorize("@ss.hasPermi('wimo:workflow:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody WimoWorkflow workflow) { return toAjax(workflowService.save(workflow)); }
}
