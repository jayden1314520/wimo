package com.ruoyi.wimo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.wimo.service.WimoDashboardService;

/** 管理端 · 数据看板 */
@RestController
@RequestMapping("/wimo/dashboard")
public class WimoDashboardController extends BaseController
{
    @Autowired
    private WimoDashboardService dashboardService;

    @PreAuthorize("@ss.hasPermi('wimo:dashboard:list')")
    @GetMapping
    public AjaxResult data(@RequestParam(value = "days", defaultValue = "7") int days)
    {
        return success(dashboardService.full(days));
    }
}
