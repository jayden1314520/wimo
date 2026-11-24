package com.ruoyi.wimo.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wimo.domain.WimoWorkflow;
import com.ruoyi.wimo.exception.WimoException;
import com.ruoyi.wimo.mapper.WimoWorkflowMapper;

/**
 * 工作流 / 智能体服务。
 *
 * <p>一个菜单 = 一个智能体。所有任务共用 WimoWorkflowEngine，
 * 但能力、可用模型、计费规则、执行步骤全部来自本表，管理端可实时调整。</p>
 *
 * @author wimo
 */
@Service
public class WimoWorkflowService
{
    private static final Logger log = LoggerFactory.getLogger(WimoWorkflowService.class);

    private static final List<String> DEFAULT_STEPS = List.of(
            "素材校验", "提示词编排", "模型推理", "后处理", "结果归档", "计费结算", "状态回写", "完成");

    @Autowired private WimoWorkflowMapper workflowMapper;
    @Autowired private ObjectMapper objectMapper;

    @PostConstruct
    public void init()
    {
        try
        {
            ClassPathResource res = new ClassPathResource("wimo/workflows.json");
            if (!res.exists()) { log.warn("wimo/workflows.json 不存在，跳过工作流初始化"); return; }
            List<Map<String, Object>> defs;
            try (InputStream in = res.getInputStream())
            {
                defs = objectMapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {});
            }
            int added = 0;
            for (Map<String, Object> d : defs)
            {
                String key = str(d.get("workflowKey"));
                if (key.isEmpty() || workflowMapper.selectByKey(key) != null) continue;
                WimoWorkflow w = new WimoWorkflow();
                w.setWorkflowKey(key);
                w.setNameZh(str(d.get("nameZh")));
                w.setNameEn(str(d.get("nameEn")));
                w.setGroupKey(str(d.getOrDefault("groupKey", "ecommerce")));
                w.setCapability(str(d.getOrDefault("capability", "img2img")));
                w.setAllowedModels(str(d.get("allowedModels")));
                w.setDefaultModel(str(d.get("defaultModel")));
                w.setBaseCost(longVal(d.get("baseCost"), 10L));
                w.setUnit(str(d.getOrDefault("unit", "count")));
                w.setUnitCost(dec(d.get("unitCost"), BigDecimal.ZERO));
                w.setUnitField(str(d.get("unitField")));
                w.setMultipliers(objectMapper.writeValueAsString(d.getOrDefault("multipliers", Map.of())));
                w.setMinCost(longVal(d.get("minCost"), 1L));
                w.setMaxCost(longVal(d.get("maxCost"), 2000L));
                w.setCostSource(str(d.getOrDefault("costSource", "override")));
                w.setStepsJson(objectMapper.writeValueAsString(d.getOrDefault("steps", DEFAULT_STEPS)));
                w.setEnabled("1");
                w.setSortNo(intVal(d.get("sortNo"), 0));
                w.setRemark(str(d.get("remark")));
                w.setCreateTime(new Date());
                w.setUpdateTime(w.getCreateTime());
                workflowMapper.insertWorkflow(w);
                added++;
            }
            log.info("映物智能体工作流初始化完成，新增 {} 个，当前共 {} 个", added, workflowMapper.countAll());
        }
        catch (Exception e)
        {
            log.error("工作流初始化失败", e);
        }
    }

    public List<WimoWorkflow> listAll() { return workflowMapper.selectAll(); }

    public WimoWorkflow getByKey(String key) { return key == null ? null : workflowMapper.selectByKey(key); }

    public WimoWorkflow getById(Long id) { return workflowMapper.selectById(id); }

    /** 智能体执行步骤（驱动 Worker 的进度推进） */
    public List<String> stepsOf(String workflowKey)
    {
        WimoWorkflow w = getByKey(workflowKey);
        if (w == null || w.getStepsJson() == null || w.getStepsJson().isBlank()) return DEFAULT_STEPS;
        try
        {
            List<String> s = objectMapper.readValue(w.getStepsJson(), new TypeReference<List<String>>() {});
            return s.isEmpty() ? DEFAULT_STEPS : s;
        }
        catch (Exception e) { return DEFAULT_STEPS; }
    }

    /** 用户端：智能体摘要（含起步价，便于表单展示） */
    public List<Map<String, Object>> publicWorkflows()
    {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WimoWorkflow w : listAll())
        {
            if (!"1".equals(w.getEnabled())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("workflowKey", w.getWorkflowKey());
            m.put("nameZh", w.getNameZh());
            m.put("nameEn", w.getNameEn());
            m.put("group", w.getGroupKey());
            m.put("capability", w.getCapability());
            m.put("baseCost", w.getBaseCost());
            m.put("unit", w.getUnit());
            m.put("unitCost", w.getUnitCost());
            m.put("minCost", w.getMinCost());
            m.put("maxCost", w.getMaxCost());
            m.put("defaultModel", w.getDefaultModel());
            out.add(m);
        }
        return out;
    }

    public int save(WimoWorkflow w)
    {
        if (w == null || w.getWorkflowId() == null) throw new WimoException("工作流不存在");
        WimoWorkflow cur = workflowMapper.selectById(w.getWorkflowId());
        if (cur == null) throw new WimoException("工作流不存在");
        // workflowKey / createTime 不可改，防止与 schema 失配
        w.setWorkflowKey(cur.getWorkflowKey());
        w.setCreateTime(cur.getCreateTime());
        if (w.getMultipliers() != null) validateJson(w.getMultipliers(), "倍率配置");
        if (w.getStepsJson() != null) validateJson(w.getStepsJson(), "步骤配置");
        if (w.getBaseCost() == null) w.setBaseCost(cur.getBaseCost());
        if (w.getMinCost() == null) w.setMinCost(cur.getMinCost());
        if (w.getMaxCost() == null) w.setMaxCost(cur.getMaxCost());
        if (w.getUnitCost() == null) w.setUnitCost(cur.getUnitCost());
        if (w.getCostSource() == null || w.getCostSource().isBlank()) w.setCostSource(cur.getCostSource());
        if (w.getMaxCost() < w.getMinCost()) throw new WimoException("最高消耗不能低于最低消耗");
        w.setUpdateTime(new Date());
        return workflowMapper.updateWorkflow(w);
    }

    private void validateJson(String json, String label)
    {
        try { objectMapper.readTree(json); }
        catch (Exception e) { throw new WimoException(label + " 不是合法 JSON：" + e.getMessage()); }
    }

    static String str(Object o) { return o == null ? "" : o.toString(); }
    static int intVal(Object o, int d) { try { return o == null ? d : Integer.parseInt(o.toString()); } catch (Exception e) { return d; } }
    static long longVal(Object o, long d) { try { return o == null ? d : Long.parseLong(o.toString()); } catch (Exception e) { return d; } }
    static BigDecimal dec(Object o, BigDecimal d) { try { return o == null ? d : new BigDecimal(o.toString()); } catch (Exception e) { return d; } }
}
