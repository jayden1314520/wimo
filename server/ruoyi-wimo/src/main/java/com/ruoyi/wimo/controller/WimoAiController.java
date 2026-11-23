package com.ruoyi.wimo.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.wimo.service.WimoAiWriterService;

/** 用户端 · AI 帮写（规则模板适配器，可替换为 LLM） */
@Anonymous
@RestController
@RequestMapping("/api/wimo/ai")
public class WimoAiController extends WimoApiSupport
{
    @Autowired
    private WimoAiWriterService aiWriter;

    @PostMapping("/write")
    public R<Map<String, Object>> write(@RequestBody Map<String, Object> body)
    {
        return wrap(() -> Map.of("text", aiWriter.write(
                s(body, "taskType"), s(body, "field"), s(body, "context"), s(body, "lang"))));
    }
}
