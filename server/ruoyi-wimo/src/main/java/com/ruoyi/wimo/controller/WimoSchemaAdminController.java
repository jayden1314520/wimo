package com.ruoyi.wimo.controller;

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
import com.ruoyi.wimo.domain.WimoSchema;
import com.ruoyi.wimo.service.WimoSchemaService;

/** 管理端 · 功能模板配置（启停 / 改名 / 编辑 schema JSON） */
@RestController
@RequestMapping("/wimo/schema")
public class WimoSchemaAdminController extends BaseController
{
    @Autowired
    private WimoSchemaService schemaService;

    @PreAuthorize("@ss.hasPermi('wimo:schema:list')")
    @GetMapping("/list")
    public AjaxResult list()
    {
        return success(schemaService.listAll());
    }

    @PreAuthorize("@ss.hasPermi('wimo:schema:list')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(schemaService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('wimo:schema:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody WimoSchema schema)
    {
        return toAjax(schemaService.update(schema));
    }

    /** 当前适配器状态，模板页展示 */
    @GetMapping("/providers")
    public AjaxResult providers()
    {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("hint", "在 application.yml 配置 wimo.* 即可切换真实服务");
        return success(m);
    }
}
