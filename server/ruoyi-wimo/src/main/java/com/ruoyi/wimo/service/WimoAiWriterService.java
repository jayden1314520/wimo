package com.ruoyi.wimo.service;

import org.springframework.stereotype.Service;

/**
 * AI 帮写适配器：默认规则模板（零依赖、离线可用）。
 * 接入 LLM 时可替换为 HTTP 调用真实模型，接口契约不变。
 */
@Service
public class WimoAiWriterService
{
    public String write(String taskType, String field, String context, String lang)
    {
        boolean zh = !"en".equalsIgnoreCase(lang);
        String type = taskType == null ? "productSet" : taskType;
        String hint = context == null ? "" : context.trim();
        if (hint.length() > 120)
        {
            hint = hint.substring(0, 120);
        }
        if (zh)
        {
            return "1. 商品/主体名称：" + (hint.isEmpty() ? "请补充主体名称" : hint) + "\n"
                    + "2. 核心卖点：突出质感、细节与差异化，建议 3 条\n"
                    + "3. 目标人群：明确年龄层与使用场景\n"
                    + "4. 期望画面：干净背景、柔和侧光、商业级棚拍质感\n"
                    + "5. 功能类型：" + type + "，请保持画面与文案一致";
        }
        return "1. Subject: " + (hint.isEmpty() ? "add a subject name" : hint) + "\n"
                + "2. Key selling points: highlight texture, details and differentiation (3 bullets)\n"
                + "3. Target audience: age group and usage scenario\n"
                + "4. Desired scene: clean backdrop, soft side light, studio-grade commercial look\n"
                + "5. Task type: " + type + " — keep visual and copy consistent";
    }
}
