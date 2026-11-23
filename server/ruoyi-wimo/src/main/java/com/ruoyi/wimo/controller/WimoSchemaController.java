package com.ruoyi.wimo.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.wimo.service.WimoSchemaService;

/** 用户端 · 功能模板列表（管理端停用/改名后用户端同步生效） */
@Anonymous
@RestController
@RequestMapping("/api/wimo/schema")
public class WimoSchemaController extends WimoApiSupport
{
    @Autowired
    private WimoSchemaService schemaService;

    @GetMapping("/list")
    public R<List<Map<String, Object>>> list()
    {
        return wrap(() -> schemaService.publicSchemas());
    }
}
