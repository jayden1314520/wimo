package com.ruoyi.wimo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.wimo.domain.WimoModel;
import com.ruoyi.wimo.service.WimoModelService;

/** 管理端 · AI 模型管理（四类能力：文生图/图生图/图生视频/文生视频） */
@RestController
@RequestMapping("/wimo/model")
public class WimoModelAdminController extends BaseController
{
    @Autowired private WimoModelService modelService;

    @PreAuthorize("@ss.hasPermi('wimo:model:list')")
    @GetMapping("/list")
    public AjaxResult list() { return success(modelService.listAll()); }

    @PreAuthorize("@ss.hasPermi('wimo:model:list')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) { return success(modelService.getById(id)); }

    @PreAuthorize("@ss.hasPermi('wimo:model:edit')")
    @PostMapping
    public AjaxResult add(@RequestBody WimoModel model)
    {
        model.setModelId(null);
        return toAjax(modelService.save(model));
    }

    @PreAuthorize("@ss.hasPermi('wimo:model:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody WimoModel model) { return toAjax(modelService.save(model)); }

    @PreAuthorize("@ss.hasPermi('wimo:model:remove')")
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) { return toAjax(modelService.remove(id)); }
}
