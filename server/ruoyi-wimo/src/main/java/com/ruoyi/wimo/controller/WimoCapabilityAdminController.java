package com.ruoyi.wimo.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.wimo.domain.WimoCapability;
import com.ruoyi.wimo.domain.dto.WimoTaskSubmit;
import com.ruoyi.wimo.service.WimoBillingEngine;
import com.ruoyi.wimo.service.WimoCapabilityService;
import com.ruoyi.wimo.service.WimoPriceNodeService;

/**
 * 管理端 · 计费规则树（能力 / 档位 / 模型 / 工作流四级）
 *
 * <p>一个接口返回整棵树，页面可展开逐节点调价；改任一层即时影响该能力下全部工作流的预估价。</p>
 *
 * @author wimo
 */
@RestController
@RequestMapping("/wimo/capability")
public class WimoCapabilityAdminController extends BaseController
{
    @Autowired private WimoCapabilityService capabilityService;
    @Autowired private WimoPriceNodeService nodeService;
    @Autowired private WimoBillingEngine billingEngine;

    /**
     * 即时试算：改完树节点后，传一个工作流 key + 表单 common，
     * 立刻返回沿树逐层求值的结果与明细，验证改价是否生效。
     */
    @PreAuthorize("@ss.hasPermi('wimo:capability:list')")
    @PostMapping("/simulate")
    public AjaxResult simulate(@RequestBody WimoTaskSubmit submit)
    {
        try
        {
            WimoBillingEngine.Quote q = billingEngine.calculate(submit.getTaskType(), submit);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cost", q.cost);
            m.put("modelKey", q.modelKey);
            m.put("breakdown", q.breakdown);
            m.put("snapshot", q.snapshot);
            return success(m);
        }
        catch (Exception e)
        {
            return error(e.getMessage() == null ? "试算失败" : e.getMessage());
        }
    }

    /** 整棵计费规则树 */
    @PreAuthorize("@ss.hasPermi('wimo:capability:list')")
    @GetMapping("/tree")
    public AjaxResult tree()
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", capabilityService.tree());
        m.put("capabilities", capabilityService.listAll().size());
        m.put("priceNodes", capabilityService.allNodes().size());
        return success(m);
    }

    @PreAuthorize("@ss.hasPermi('wimo:capability:list')")
    @GetMapping("/list")
    public AjaxResult list() { return success(capabilityService.listAll()); }

    @PreAuthorize("@ss.hasPermi('wimo:capability:list')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) { return success(capabilityService.getById(id)); }

    /** 编辑能力基础价 / 单位 / 上下限（即时生效） */
    @PreAuthorize("@ss.hasPermi('wimo:capability:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody WimoCapability capability)
    {
        return toAjax(capabilityService.save(capability));
    }

    /** 重新从 JSON 灌入能力与档位（不清空已有数据） */
    @PreAuthorize("@ss.hasPermi('wimo:capability:edit')")
    @PostMapping("/reload")
    public AjaxResult reload() { return success(capabilityService.reload()); }

    /** 某能力下原始节点列表（编辑弹窗用） */
    @PreAuthorize("@ss.hasPermi('wimo:capability:list')")
    @GetMapping("/node/list")
    public AjaxResult nodeList(@RequestParam(required = false) String capabilityKey)
    {
        return success(capabilityKey == null || capabilityKey.isEmpty()
                ? nodeService.listAll() : nodeService.nodesOf(capabilityKey));
    }

    /** 编辑档位节点：倍率 / 单价 / 启停 / 标签（即时生效） */
    @PreAuthorize("@ss.hasPermi('wimo:capability:edit')")
    @PutMapping("/node")
    public AjaxResult editNode(@RequestBody com.ruoyi.wimo.domain.WimoPriceNode node)
    {
        return toAjax(nodeService.save(node));
    }

    /** 新增档位节点 */
    @PreAuthorize("@ss.hasPermi('wimo:capability:edit')")
    @PostMapping("/node")
    public AjaxResult addNode(@RequestBody com.ruoyi.wimo.domain.WimoPriceNode node)
    {
        node.setNodeId(null);
        return toAjax(nodeService.save(node));
    }

    /** 启停档位节点 */
    @PreAuthorize("@ss.hasPermi('wimo:capability:edit')")
    @PutMapping("/node/toggle")
    public AjaxResult toggleNode(@RequestBody Map<String, Object> body)
    {
        Object id = body.get("nodeId");
        Object enabled = body.get("enabled");
        if (id == null) return error("缺少 nodeId");
        return toAjax(nodeService.toggle(Long.parseLong(id.toString()), String.valueOf(enabled)));
    }

    /** 删除档位节点（删分组会连带删除其下档位） */
    @PreAuthorize("@ss.hasPermi('wimo:capability:edit')")
    @org.springframework.web.bind.annotation.DeleteMapping("/node/{id}")
    public AjaxResult removeNode(@PathVariable("id") Long id)
    {
        return toAjax(nodeService.remove(id));
    }
}
