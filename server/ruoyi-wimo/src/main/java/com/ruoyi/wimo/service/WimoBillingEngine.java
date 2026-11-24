package com.ruoyi.wimo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wimo.domain.WimoCapability;
import com.ruoyi.wimo.domain.WimoModel;
import com.ruoyi.wimo.domain.WimoPriceNode;
import com.ruoyi.wimo.domain.WimoWorkflow;
import com.ruoyi.wimo.domain.dto.WimoTaskSubmit;
import com.ruoyi.wimo.exception.WimoException;

/**
 * 通用计费引擎 —— 沿「钻石计费规则树」逐层求值（一次生成消耗多少钻石）。
 *
 * <p>树：能力(wimo_capability) → 维度档位(wimo_price_node) / 模型(wimo_model) / 工作流(wimo_workflow)。
 * 求值顺序：</p>
 * <pre>
 *   1 能力默认   该能力分支的 baseCost/unitCost/minCost/maxCost（每一支不同）
 *   2 工作流覆盖 workflow.costSource=override 用自身基础价；=inherit 继承能力层
 *   3 维度档位   从 wimo_price_node 按(能力,维度)取选中档位 factor 连乘；改一个 4K 档即影响该能力全部工作流
 *   4 单位计价   base + unitCost × units（视频按秒 / 图片按张 / 文案按千字）
 *   5 模型倍率   wimo_model.costMultiplier
 *   6 取整限价   clamp(ceil(...), minCost, maxCost)
 * </pre>
 *
 * <p>向后兼容：若某能力下没有任何启用的价格节点，则回退到 workflow.multipliers 旧 JSON。
 * 所有档位倍率与既有工作流配置完全对齐，因此切换后历史询价结果不变。</p>
 *
 * <p>每次算价生成完整 billing_snapshot + breakdown（逐层取值与倍率），管理端与用户端可追溯
 * 「为什么扣了这么多」，且后续调整规则不影响历史账单。</p>
 *
 * @author wimo
 */
@Service
public class WimoBillingEngine
{
    @Autowired private WimoWorkflowService workflowService;
    @Autowired private WimoModelService modelService;
    @Autowired private WimoCapabilityService capabilityService;
    @Autowired private WimoDiamondService diamondService;
    @Autowired private ObjectMapper objectMapper;

    /** 计价结果（内部载体） */
    public static class Quote
    {
        public long cost;
        public String modelKey;
        public BigDecimal modelFactor = BigDecimal.ONE;
        public Map<String, Object> snapshot = new LinkedHashMap<>();
        /** 逐层求值明细（用户端费用拆解 / 管理端即时预览） */
        public List<Map<String, Object>> breakdown = new ArrayList<>();
    }

    /** 按提交参数计算本次消耗（不查余额，供预估与下单共用） */
    public Quote calculate(String taskType, WimoTaskSubmit submit)
    {
        WimoWorkflow wf = workflowService.getByKey(taskType);
        if (wf == null) throw new WimoException("未找到智能体工作流：" + taskType);
        if (!"1".equals(wf.getEnabled())) throw new WimoException("该智能体已停用");

        Map<String, Object> common = submit.getCommon() == null ? Map.of() : submit.getCommon();
        Map<String, Object> values = submit.getValues() == null ? Map.of() : submit.getValues();

        String capKey = wf.getCapability();
        WimoCapability cap = capabilityService.getByKey(capKey);

        // ---- 1) 选模型 ----
        List<WimoModel> available = modelService.modelsFor(wf);
        String requested = firstNonEmpty(str(common.get("model")), str(common.get("videoModel")));
        WimoModel chosen = pickModel(available, requested, wf.getDefaultModel());

        // ---- 2) 价格来源：工作流覆盖 or 继承能力层 ----
        boolean inherit = cap != null && "inherit".equals(wf.getCostSource());
        BigDecimal base;
        BigDecimal unitCost;
        String unit;
        String unitField;
        long min;
        long max;
        if (inherit)
        {
            base = BigDecimal.valueOf(cap.getBaseCost() == null ? 0 : cap.getBaseCost());
            unitCost = cap.getUnitCost() == null ? BigDecimal.ZERO : cap.getUnitCost();
            unit = nz(cap.getUnit(), "count");
            unitField = nz(cap.getUnitField(), "resultCount");
            min = cap.getMinCost() == null ? 1 : cap.getMinCost();
            max = cap.getMaxCost() == null ? 2000 : cap.getMaxCost();
        }
        else
        {
            base = BigDecimal.valueOf(wf.getBaseCost() == null ? 0 : wf.getBaseCost());
            unitCost = wf.getUnitCost() == null ? BigDecimal.ZERO : wf.getUnitCost();
            unit = nz(wf.getUnit(), "count");
            unitField = nz(wf.getUnitField(), "resultCount");
            min = wf.getMinCost() == null ? 1 : wf.getMinCost();
            max = wf.getMaxCost() == null ? 2000 : wf.getMaxCost();
        }

        // ---- 3) 维度档位：能力层价格节点优先，无节点回退旧 multipliers ----
        List<DimFactor> dims = resolveDimensionFactors(capKey, wf, unitField, common, values, unitCost);
        BigDecimal factorProduct = BigDecimal.ONE;
        for (DimFactor d : dims) factorProduct = factorProduct.multiply(BigDecimal.valueOf(d.factor));

        // ---- 4) 单位计价 ----
        long units = resolveUnits(unit, unitField, common, values);
        BigDecimal subtotal = base.add(unitCost.multiply(BigDecimal.valueOf(units)));
        BigDecimal factored = subtotal.multiply(factorProduct);

        // ---- 5) 模型倍率 ----
        BigDecimal modelFactor = chosen == null || chosen.getCostMultiplier() == null
                ? BigDecimal.ONE : chosen.getCostMultiplier();
        BigDecimal raw = factored.multiply(modelFactor);

        // ---- 6) 向上取整 + 上下限 ----
        long cost = raw.setScale(0, RoundingMode.CEILING).longValueExact();
        cost = Math.max(min, Math.min(max, cost));

        Quote q = new Quote();
        q.cost = cost;
        q.modelKey = chosen == null ? "" : chosen.getModelKey();
        q.modelFactor = modelFactor;

        // ---- 组装 breakdown（逐层小计递进） ----
        List<Map<String, Object>> bd = q.breakdown;
        BigDecimal running = base;
        Map<String, Object> s0 = new LinkedHashMap<>();
        s0.put("layer", "capability");
        s0.put("labelZh", "能力基础价");
        s0.put("labelEn", "Base cost");
        s0.put("value", capKey);
        s0.put("costSource", inherit ? "inherit" : "override");
        s0.put("amount", base);
        s0.put("subtotal", running);
        bd.add(s0);

        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("layer", "unit");
        s1.put("labelZh", "单位计价");
        s1.put("labelEn", "Units");
        s1.put("unit", unit);
        s1.put("units", units);
        s1.put("unitCost", unitCost);
        running = subtotal;
        s1.put("subtotal", running);
        bd.add(s1);

        for (DimFactor d : dims)
        {
            running = running.multiply(BigDecimal.valueOf(d.factor));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("layer", unitField.equals(d.dimension) ? "unit-adjust" : "dimension");
            item.put("dimension", d.dimension);
            item.put("labelZh", d.labelZh);
            item.put("labelEn", d.labelEn);
            item.put("option", d.option);
            item.put("optionLabelZh", d.optionLabelZh);
            item.put("optionLabelEn", d.optionLabelEn);
            item.put("matched", d.matched);
            item.put("factor", d.factor);
            item.put("subtotal", running);
            bd.add(item);
        }

        running = raw;
        Map<String, Object> sm = new LinkedHashMap<>();
        sm.put("layer", "model");
        sm.put("labelZh", "模型倍率");
        sm.put("labelEn", "Model factor");
        sm.put("modelKey", q.modelKey);
        sm.put("factor", modelFactor);
        sm.put("subtotal", running);
        bd.add(sm);

        Map<String, Object> sc = new LinkedHashMap<>();
        sc.put("layer", "clamp");
        sc.put("labelZh", "取整 / 限价");
        sc.put("labelEn", "Round & clamp");
        sc.put("beforeCeil", raw);
        sc.put("minCost", min);
        sc.put("maxCost", max);
        sc.put("total", cost);
        bd.add(sc);

        // ---- 快照（保留全部旧字段，新增能力层与树信息） ----
        Map<String, Object> snap = q.snapshot;
        snap.put("workflowKey", wf.getWorkflowKey());
        snap.put("workflowName", wf.getNameZh());
        snap.put("capability", capKey);
        snap.put("capabilityName", cap == null ? capKey : cap.getNameZh());
        snap.put("costSource", inherit ? "inherit" : "override");
        snap.put("priceSource", dims.isEmpty() ? "workflow-multipliers" : "capability-tree");
        snap.put("treeEnabled", !dims.isEmpty());
        snap.put("modelKey", q.modelKey);
        snap.put("modelFactor", modelFactor);
        snap.put("baseCost", base);
        snap.put("unit", unit);
        snap.put("unitField", unitField);
        snap.put("units", units);
        snap.put("unitCost", unitCost);
        snap.put("subtotal", subtotal);
        snap.put("factors", dims.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("field", d.dimension); m.put("value", d.option);
            m.put("factor", d.factor); m.put("matched", d.matched);
            return m;
        }).collect(Collectors.toList()));
        snap.put("factorProduct", factorProduct);
        snap.put("beforeCeil", raw);
        snap.put("minCost", min);
        snap.put("maxCost", max);
        snap.put("total", cost);
        snap.put("breakdown", bd);
        snap.put("formula", "clamp(ceil((capability.base + unitCost*units) * Π node.factor * model.factor), min, max)");
        snap.put("quotedAt", new Date());
        return q;
    }

    /** 用户端预估价：含余额、是否足够、逐层明细 */
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
        out.put("enough", anonymous || bal >= q.cost);
        out.put("snapshot", q.snapshot);
        out.put("breakdown", q.breakdown);
        out.put("workflowKey", taskType);
        return out;
    }

    /** 维度因子载体 */
    private static class DimFactor
    {
        String dimension;
        String labelZh;
        String labelEn;
        String option = "";
        String optionLabelZh = "";
        String optionLabelEn = "";
        boolean matched;
        double factor = 1.0;
    }

    /**
     * 解析各维度档位因子：
     * 能力层有启用价格节点 → 按(能力,维度)分组逐维度匹配；
     * 否则回退到 workflow.multipliers 旧 JSON（兼容未初始化的老库）。
     */
    private List<DimFactor> resolveDimensionFactors(String capKey, WimoWorkflow wf, String unitField,
                                                     Map<String, Object> common, Map<String, Object> values,
                                                     BigDecimal unitCost)
    {
        List<WimoPriceNode> nodes = capabilityService.enabledNodes(capKey);
        if (!nodes.isEmpty())
        {
            // 按维度分组（LinkedHashMap 保序）
            Map<String, List<WimoPriceNode>> byDim = nodes.stream()
                    .collect(Collectors.groupingBy(WimoPriceNode::getDimension, LinkedHashMap::new, Collectors.toList()));
            List<DimFactor> out = new ArrayList<>();
            for (Map.Entry<String, List<WimoPriceNode>> e : byDim.entrySet())
            {
                String dim = e.getKey();
                // 模型维度由 wimo_model 单独处理，不作为价格节点因子
                if ("model".equals(dim)) continue;
                String val = lookupDim(dim, common, values);
                WimoPriceNode hit = matchNode(e.getValue(), val);
                DimFactor d = new DimFactor();
                d.dimension = dim;
                String[] label = WimoCapabilityService.DIM_LABEL.getOrDefault(dim, new String[] { dim, dim });
                d.labelZh = label[0];
                d.labelEn = label[1];
                d.option = val == null ? "" : val;
                d.matched = hit != null;
                d.factor = hit == null || hit.getFactor() == null ? 1.0 : hit.getFactor().doubleValue();
                if (hit != null)
                {
                    d.optionLabelZh = nz(hit.getLabelZh(), d.option);
                    d.optionLabelEn = nz(hit.getLabelEn(), d.option);
                }
                out.add(d);
            }
            return out;
        }

        // 回退：旧 multipliers JSON
        Map<String, Map<String, Double>> multipliers = parseMultipliers(wf.getMultipliers());
        List<DimFactor> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> e : multipliers.entrySet())
        {
            String field = e.getKey();
            String val = lookupDim(field, common, values);
            double f = 1.0;
            Map<String, Double> valueMap = e.getValue();
            if (valueMap != null && val != null && valueMap.containsKey(val)) f = valueMap.get(val);
            DimFactor d = new DimFactor();
            d.dimension = field;
            String[] label = WimoCapabilityService.DIM_LABEL.getOrDefault(field, new String[] { field, field });
            d.labelZh = label[0];
            d.labelEn = label[1];
            d.option = val == null ? "" : val;
            d.optionLabelZh = d.option;
            d.optionLabelEn = d.option;
            d.matched = valueMap != null && val != null && valueMap.containsKey(val);
            d.factor = f;
            out.add(d);
        }
        return out;
    }

    /** 档位匹配：先 optionValue 精确匹配，再数值区间匹配（字数等） */
    private WimoPriceNode matchNode(List<WimoPriceNode> opts, String val)
    {
        if (val == null || val.isEmpty()) return null;
        for (WimoPriceNode n : opts)
        {
            if (val.equals(n.getOptionValue())) return n;
        }
        try
        {
            long v = Long.parseLong(val);
            for (WimoPriceNode n : opts)
            {
                if (n.getMinValue() != null && n.getMaxValue() != null
                        && v >= n.getMinValue() && v <= n.getMaxValue()) return n;
            }
        }
        catch (NumberFormatException ignored) { }
        return null;
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

    private long resolveUnits(String unit, String unitField, Map<String, Object> common, Map<String, Object> values)
    {
        if ("flat".equals(unit)) return 0L;
        Object raw = unitField == null || unitField.isEmpty() ? null : common.get(unitField);
        if (raw == null && unitField != null) raw = values.get(unitField);
        long n;
        try { n = raw == null ? -1 : Long.parseLong(raw.toString()); }
        catch (Exception e) { n = -1; }
        if (n < 0) n = "second".equals(unit) ? 5L : 4L; // 图片默认 4 张，视频默认 5 秒
        if ("second".equals(unit)) return Math.max(1, Math.min(600, n));
        if ("kiloword".equals(unit)) return Math.max(1, Math.min(100, (n + 999) / 1000));
        return Math.max(1, Math.min(20, n)); // count：图片结果张数，上限 20
    }

    /** 维度取值：model/videoModel 归一到模型字段，其余直接读 common/values */
    private String lookupDim(String dim, Map<String, Object> common, Map<String, Object> values)
    {
        if ("model".equals(dim)) return firstNonEmpty(str(common.get("model")), str(common.get("videoModel")));
        Object v = common.get(dim);
        if (v == null) v = values.get(dim);
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
    private String nz(String s, String d) { return s == null || s.isBlank() ? d : s; }
}
