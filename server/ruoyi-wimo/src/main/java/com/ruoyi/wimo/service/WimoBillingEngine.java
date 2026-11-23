package com.ruoyi.wimo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wimo.domain.WimoModel;
import com.ruoyi.wimo.domain.WimoWorkflow;
import com.ruoyi.wimo.domain.dto.WimoTaskSubmit;
import com.ruoyi.wimo.exception.WimoException;

/**
 * 通用计费引擎（一次生成消耗多少钻石）
 *
 * <p>公式（所有智能体共用，参数由 wimo_workflow 逐条配置）：</p>
 * <pre>
 *   cost = clamp( ceil( (baseCost + unitCost × units) × Π factor(维度) × modelFactor ), minCost, maxCost )
 *
 *   units   : 图片按结果张数（resultCount，默认 4）／视频按秒（duration，默认 5）／flat 固定为 0
 *   factor  : 维度倍率，来自 workflow.multipliers
 *             图片：resolution(2k=1.0,4k=1.6)、quality(low=0.8,mid=1.0,high=1.4)
 *             视频：videoResolution(480p=1.0,720p=1.4,1080p=1.8,2k=2.4,4k=3.2)
 *   modelFactor : 模型 costMultiplier（管理端为每个模型单独配置）
 * </pre>
 *
 * <p>示例：探店视频 720P / 30 秒 / Seedance 极速(1.0)
 * → (22 + 1.6×30) × 1.4 × 1.0 = ceil(98) = 98 钻石。</p>
 *
 * <p>每次算价都会生成完整 billing_snapshot 落库，管理端可追溯"为什么扣了这么多"，
 * 且后续调整规则不影响历史账单。</p>
 *
 * @author wimo
 */
@Service
public class WimoBillingEngine
{
    @Autowired private WimoWorkflowService workflowService;
    @Autowired private WimoModelService modelService;
    @Autowired private WimoDiamondService diamondService;
    @Autowired private ObjectMapper objectMapper;

    /** 计价结果（内部载体） */
    public static class Quote
    {
        public long cost;
        public String modelKey;
        public BigDecimal modelFactor = BigDecimal.ONE;
        public Map<String, Object> snapshot = new LinkedHashMap<>();
    }

    /** 按提交参数计算本次消耗（不查余额，供预估与下单共用） */
    public Quote calculate(String taskType, WimoTaskSubmit submit)
    {
        WimoWorkflow wf = workflowService.getByKey(taskType);
        if (wf == null) throw new WimoException("未找到智能体工作流：" + taskType);
        if (!"1".equals(wf.getEnabled())) throw new WimoException("该智能体已停用");

        Map<String, Object> common = submit.getCommon() == null ? Map.of() : submit.getCommon();
        Map<String, Object> values = submit.getValues() == null ? Map.of() : submit.getValues();

        // 1) 选模型：优先前端选择，非法或未传则回退工作流默认模型 / 该能力首个模型
        List<WimoModel> available = modelService.modelsFor(wf);
        String requested = firstNonEmpty(str(common.get("model")), str(common.get("videoModel")));
        WimoModel chosen = pickModel(available, requested, wf.getDefaultModel());

        // 2) 单位数
        long units = resolveUnits(wf, common, values, submit);

        // 3) 基础小计
        BigDecimal base = BigDecimal.valueOf(wf.getBaseCost() == null ? 0 : wf.getBaseCost());
        BigDecimal unitCost = wf.getUnitCost() == null ? BigDecimal.ZERO : wf.getUnitCost();
        BigDecimal subtotal = base.add(unitCost.multiply(BigDecimal.valueOf(units)));

        // 4) 维度倍率（清晰度 / 画质 / 时长档次等，完全由管理端配置驱动）
        Map<String, Map<String, Double>> multipliers = parseMultipliers(wf.getMultipliers());
        List<Map<String, Object>> factors = new ArrayList<>();
        BigDecimal factored = subtotal;
        for (Map.Entry<String, Map<String, Double>> e : multipliers.entrySet())
        {
            String field = e.getKey();
            String val = lookup(field, common, values);
            double f = 1.0;
            Map<String, Double> valueMap = e.getValue();
            if (valueMap != null && val != null && valueMap.containsKey(val))
            {
                f = valueMap.get(val);
            }
            factored = factored.multiply(BigDecimal.valueOf(f));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", field);
            item.put("value", val == null ? "" : val);
            item.put("factor", f);
            factors.add(item);
        }

        // 5) 模型倍率
        BigDecimal modelFactor = chosen == null || chosen.getCostMultiplier() == null
                ? BigDecimal.ONE : chosen.getCostMultiplier();
        BigDecimal raw = factored.multiply(modelFactor);

        // 6) 向上取整 + 上下限
        long cost = raw.setScale(0, RoundingMode.CEILING).longValueExact();
        long min = wf.getMinCost() == null ? 1 : wf.getMinCost();
        long max = wf.getMaxCost() == null ? 2000 : wf.getMaxCost();
        cost = Math.max(min, Math.min(max, cost));

        Quote q = new Quote();
        q.cost = cost;
        q.modelKey = chosen == null ? "" : chosen.getModelKey();
        q.modelFactor = modelFactor;

        Map<String, Object> snap = q.snapshot;
        snap.put("workflowKey", wf.getWorkflowKey());
        snap.put("workflowName", wf.getNameZh());
        snap.put("capability", wf.getCapability());
        snap.put("modelKey", q.modelKey);
        snap.put("modelFactor", modelFactor);
        snap.put("baseCost", base);
        snap.put("unit", wf.getUnit());
        snap.put("unitField", wf.getUnitField());
        snap.put("units", units);
        snap.put("unitCost", unitCost);
        snap.put("subtotal", subtotal);
        snap.put("factors", factors);
        snap.put("beforeCeil", raw);
        snap.put("minCost", min);
        snap.put("maxCost", max);
        snap.put("total", cost);
        snap.put("formula", "clamp(ceil((base + unitCost*units) * Πfactors * modelFactor), min, max)");
        snap.put("quotedAt", new Date());
        return q;
    }

    /** 用户端预估价：含余额、是否足够、可选模型 */
    public Map<String, Object> quote(String taskType, WimoTaskSubmit submit, Long userId)
    {
        Quote q = calculate(taskType, submit);
        Map<String, Object> balance = diamondService.balanceOf(userId);
        boolean anonymous = Boolean.TRUE.equals(balance.get("anonymous"));
        long bal = ((Number) balance.getOrDefault("balance", 0L)).longValue();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cost", q.cost);
        out.put("modelKey", q.modelKey);
        out.put("balance", bal);
        out.put("anonymous", anonymous);
        // 匿名演示用户不计费，永远可提交
        out.put("enough", anonymous || bal >= q.cost);
        out.put("snapshot", q.snapshot);
        out.put("workflowKey", taskType);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Double>> parseMultipliers(String json)
    {
        if (json == null || json.isBlank()) return Map.of();
        try
        {
            return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, Double>>>() {});
        }
        catch (Exception e)
        {
            throw new WimoException("工作流倍率配置不是合法 JSON：" + e.getMessage());
        }
    }

    private long resolveUnits(WimoWorkflow wf, Map<String, Object> common, Map<String, Object> values, WimoTaskSubmit submit)
    {
        String unit = wf.getUnit() == null ? "flat" : wf.getUnit();
        if ("flat".equals(unit)) return 0L;
        String field = wf.getUnitField();
        Object raw = field == null || field.isEmpty() ? null : common.get(field);
        if (raw == null && field != null) raw = values.get(field);
        long n;
        try { n = raw == null ? -1 : Long.parseLong(raw.toString()); }
        catch (Exception e) { n = -1; }
        if (n < 0)
        {
            // 图片默认 4 张，视频默认 5 秒
            n = "second".equals(unit) ? 5L : 4L;
        }
        if ("second".equals(unit)) return Math.max(1, Math.min(600, n));
        // count：图片结果张数，上限 20
        return Math.max(1, Math.min(20, n));
    }

    private String lookup(String field, Map<String, Object> common, Map<String, Object> values)
    {
        Object v = common.get(field);
        if (v == null) v = values.get(field);
        return v == null ? "" : v.toString();
    }

    private WimoModel pickModel(List<WimoModel> available, String requested, String defaultKey)
    {
        if (available == null || available.isEmpty()) return null;
        if (requested != null && !requested.isEmpty())
        {
            for (WimoModel m : available) if (requested.equals(m.getModelKey())) return m;
        }
        if (defaultKey != null && !defaultKey.isEmpty())
        {
            for (WimoModel m : available) if (defaultKey.equals(m.getModelKey())) return m;
        }
        return available.get(0);
    }

    private String firstNonEmpty(String a, String b)
    {
        return a != null && !a.isEmpty() ? a : (b == null ? "" : b);
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }
}
