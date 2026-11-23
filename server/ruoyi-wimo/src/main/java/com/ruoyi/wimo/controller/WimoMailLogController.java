package com.ruoyi.wimo.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.wimo.domain.WimoMailLog;
import com.ruoyi.wimo.mapper.WimoMailLogMapper;

/** 管理端 · 验证码邮件日志 */
@RestController
@RequestMapping("/wimo/mail")
public class WimoMailLogController extends BaseController
{
    @Autowired
    private WimoMailLogMapper mailLogMapper;

    @PreAuthorize("@ss.hasPermi('wimo:mail:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(value = "email", required = false) String email,
                           @RequestParam(value = "scene", required = false) String scene,
                           @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                           @RequestParam(value = "pageSize", defaultValue = "10") int pageSize)
    {
        int p = Math.max(1, pageNum);
        int s = Math.min(100, Math.max(1, pageSize));
        List<WimoMailLog> rows = mailLogMapper.selectList(email, scene, (p - 1) * s, s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", mailLogMapper.countList(email, scene));
        m.put("rows", rows);
        m.put("pageNum", p);
        m.put("pageSize", s);
        return success(m);
    }
}
