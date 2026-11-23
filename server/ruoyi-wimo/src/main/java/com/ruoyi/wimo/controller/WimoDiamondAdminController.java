package com.ruoyi.wimo.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.wimo.mapper.WimoUserMapper;
import com.ruoyi.wimo.service.WimoDiamondService;

/** 管理端 · 钻石钱包：会员余额、流水、人工调整、看板总览 */
@RestController
@RequestMapping("/wimo/diamond")
public class WimoDiamondAdminController extends BaseController
{
    @Autowired private WimoDiamondService diamondService;
    @Autowired private WimoUserMapper userMapper;

    @PreAuthorize("@ss.hasPermi('wimo:diamond:list')")
    @GetMapping("/users")
    public AjaxResult users() { return success(userMapper.selectAll()); }

    @PreAuthorize("@ss.hasPermi('wimo:diamond:list')")
    @GetMapping("/logs")
    public AjaxResult logs(@RequestParam(required = false) Long userId,
                           @RequestParam(required = false) String changeType,
                           @RequestParam(defaultValue = "1") int pageNum,
                           @RequestParam(defaultValue = "10") int pageSize)
    {
        return success(diamondService.pageLogs(userId, changeType, pageNum, pageSize));
    }

    @PreAuthorize("@ss.hasPermi('wimo:diamond:list')")
    @GetMapping("/overview")
    public AjaxResult overview() { return success(diamondService.overview()); }

    /** 赠送 / 扣减；delta 可正可负 */
    @PreAuthorize("@ss.hasPermi('wimo:diamond:edit')")
    @PostMapping("/adjust")
    public AjaxResult adjust(@RequestBody Map<String, Object> body)
    {
        Object uid = body.get("userId");
        Object delta = body.get("delta");
        if (uid == null || delta == null) return error("缺少 userId 或 delta");
        try
        {
            long d = Long.parseLong(delta.toString());
            String remark = body.get("remark") == null ? "" : body.get("remark").toString();
            Map<String, Object> m = diamondService.adjust(Long.parseLong(uid.toString()), d, getUsername(), remark);
            return success(m);
        }
        catch (Exception e)
        {
            return error(e.getMessage() == null ? "调整失败" : e.getMessage());
        }
    }

    /** 前端下拉用：能力/流水类型说明 */
    @GetMapping("/meta")
    public AjaxResult meta()
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("changeTypes", Map.of(
                "gift", "注册赠送", "recharge", "充值", "freeze", "生成预扣",
                "consume", "生成扣减", "refund", "失败退还", "adjust", "人工调整"));
        return success(m);
    }
}
