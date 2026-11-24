package com.ruoyi.wimo.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wimo.domain.WimoCapability;
import com.ruoyi.wimo.domain.WimoPriceNode;
import com.ruoyi.wimo.exception.WimoException;
import com.ruoyi.wimo.mapper.WimoCapabilityMapper;
import com.ruoyi.wimo.mapper.WimoModelMapper;
import com.ruoyi.wimo.mapper.WimoPriceNodeMapper;
import com.ruoyi.wimo.mapper.WimoWorkflowMapper;

/**
 * 能力层 + 计费规则树服务。
 *
 * <p>「能力 = 计费规则树的树根」。启动时从 capabilities.json 初始化 5 个能力分支，
 * 从 price-nodes.json 初始化维度档位节点，并按 (能力, 维度) 自动生成维度分组(group)节点，
 * 使 wimo_price_node 通过 parent_id 自关联成：能力(虚拟根) → 维度分组 → 具体档位。</p>
 *
 * <p>对外组装四级树：能力 → 维度档位 / 模型 / 工作流，供管理端「计费规则树」页与用户端
 * 「费用明细拆解」使用。计费引擎 WimoBillingEngine 从这里取维度档位倍率逐层求值。</p>
 *
 * @author wimo
 */
@Service
public class WimoCapabilityService
{
    private static final Logger log = LoggerFactory.getLogger(WimoCapabilityService.class);

    /** 维度中文/英文标签（用于自动生成分组节点与用户端明细展示） */
    public static final Map<String, String[]> DIM_LABEL = new LinkedHashMap<>();
    static
    {
        DIM_LABEL.put("resolution",      new String[] { "图片清晰度", "Resolution" });
        DIM_LABEL.put("videoResolution", new String[] { "视频清晰度", "Video Resolution" });
        DIM_LABEL.put("quality",         new String[] { "画质", "Quality" });
        DIM_LABEL.put("duration",        new String[] { "时长", "Duration" });
        DIM_LABEL.put("wordCount",       new String[] { "字数", "Word Count" });
        DIM_LABEL.put("language",        new String[] { "语言", "Language" });
        DIM_LABEL.put("model",           new String[] { "模型", "Model" });
    }

    @Autowired private WimoCapabilityMapper capabilityMapper;
    @Autowired private WimoPriceNodeMapper nodeMapper;
    @Autowired private WimoModelMapper modelMapper;
    @Autowired private WimoWorkflowMapper workflowMapper;
    @Autowired private ObjectMapper objectMapper;

    @PostConstruct
    public void init()
    {
        initCapabilities();
        initPriceNodes();
    }

    /** 能力层初始化（已存在跳过，不覆盖管理端改价） */
    private void initCapabilities()
    {
        try
        {
            ClassPathResource res = new ClassPathResource("wimo/capabilities.json");
            if (!res.exists()) { log.warn("wimo/capabilities.json 不存在，跳过能力层初始化"); return; }
            List<Map<String, Object>> defs;
            try (InputStream in = res.getInputStream())
            {
                defs = objectMapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {});
            }
            int added = 0;
            for (Map<String, Object> d : defs)
            {
                String key = str(d.get("capabilityKey"));
                if (key.isEmpty() || capabilityMapper.selectByKey(key) != null) continue;
                WimoCapability c = new WimoCapability();
                c.setCapabilityKey(key);
                c.setNameZh(str(d.get("nameZh")));
                c.setNameEn(str(d.get("nameEn")));
                c.setIcon(str(d.get("icon")));
                c.setBaseCost(longVal(d.get("baseCost"), 10L));
                c.setUnit(str(d.getOrDefault("defaultUnit", "count")));
                c.setUnitCost(dec(d.get("unitCost"), BigDecimal.ZERO));
                c.setUnitField(str(d.getOrDefault("defaultUnitField", "")));
                c.setMinCost(longVal(d.get("minCost"), 1L));
                c.setMaxCost(longVal(d.get("maxCost"), 2000L));
                c.setDefaultModel(str(d.get("defaultModel")));
                c.setEnabled(str(d.getOrDefault("enabled", "1")));
                c.setSortNo(intVal(d.get("sortNo"), 0));
                c.setRemark(str(d.get("remark")));
                c.setCreateTime(new Date());
                c.setUpdateTime(c.getCreateTime());
                capabilityMapper.insertCapability(c);
                added++;
            }
            log.info("映物能力层初始化完成，新增 {} 个，当前共 {} 个", added, capabilityMapper.countAll());
        }
        catch (Exception e)
        {
            log.error("能力层初始化失败", e);
        }
    }

    /** 价格节点初始化：按(能力,维度)生成分组节点 + 档位节点，全部幂等 */
    private void initPriceNodes()
    {
        try
        {
            ClassPathResource res = new ClassPathResource("wimo/price-nodes.json");
            if (!res.exists()) { log.warn("wimo/price-nodes.json 不存在，跳过价格节点初始化"); return; }
            List<Map<String, Object>> defs;
            try (InputStream in = res.getInputStream())
            {
                defs = objectMapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {});
            }
            // 收集所有 (能力, 维度) 组合，先建分组节点
            Set<String> groups = new LinkedHashSet<>();
            for (Map<String, Object> d : defs)
            {
                groups.add(str(d.get("capabilityKey")) + "|" + str(d.get("dimension")));
            }
            int groupAdded = 0;
            for (String g : groups)
            {
                String[] p = g.split("\\|", 2);
                String cap = p[0], dim = p.length > 1 ? p[1] : "";
                String gKey = cap + ":" + dim + ":group";
                if (nodeMapper.selectByKey(gKey) != null) continue;
                String[] label = DIM_LABEL.getOrDefault(dim, new String[] { dim, dim });
                WimoPriceNode group = new WimoPriceNode();
                group.setParentId(0L);
                group.setCapabilityKey(cap);
                group.setNodeType("group");
                group.setDimension(dim);
                group.setNodeKey(gKey);
                group.setOptionValue("");
                group.setLabelZh(label[0]);
                group.setLabelEn(label[1]);
                group.setFactor(BigDecimal.ONE);
                group.setEnabled("1");
                group.setSortNo(0);
                group.setCreateTime(new Date());
                group.setUpdateTime(group.getCreateTime());
                nodeMapper.insertNode(group);
                groupAdded++;
            }
            // 建档位节点，parentId 指向对应分组
            int optionAdded = 0;
            for (Map<String, Object> d : defs)
            {
                String nKey = str(d.get("nodeKey"));
                if (nKey.isEmpty() || nodeMapper.selectByKey(nKey) != null) continue;
                String cap = str(d.get("capabilityKey"));
                String dim = str(d.get("dimension"));
                WimoPriceNode group = nodeMapper.selectByKey(cap + ":" + dim + ":group");
                WimoPriceNode n = new WimoPriceNode();
                n.setParentId(group == null ? 0L : group.getNodeId());
                n.setCapabilityKey(cap);
                n.setNodeType("option");
                n.setDimension(dim);
                n.setNodeKey(nKey);
                n.setOptionValue(str(d.get("optionValue")));
                n.setLabelZh(str(d.get("labelZh")));
                n.setLabelEn(str(d.get("labelEn")));
                n.setFactor(dec(d.get("factor"), BigDecimal.ONE));
                Object uc = d.get("unitCost");
                n.setUnitCost(uc == null ? null : new BigDecimal(uc.toString()));
                Object minv = d.get("minValue");
                Object maxv = d.get("maxValue");
                n.setMinValue(minv == null ? null : Long.parseLong(minv.toString()));
                n.setMaxValue(maxv == null ? null : Long.parseLong(maxv.toString()));
                n.setEnabled("1");
                n.setSortNo(intVal(d.get("sortNo"), 0));
                n.setCreateTime(new Date());
                n.setUpdateTime(n.getCreateTime());
                nodeMapper.insertNode(n);
                optionAdded++;
            }
            log.info("映物价格节点初始化完成，新增分组 {} 个 / 档位 {} 个，当前共 {} 个",
                    groupAdded, optionAdded, nodeMapper.countAll());
        }
        catch (Exception e)
        {
            log.error("价格节点初始化失败", e);
        }
    }

    public List<WimoCapability> listAll() { return safe(() -> capabilityMapper.selectAll(), List.of()); }

    /** 老库(v2)未建能力表时返回 null，计费引擎自动回退到工作流自身的旧配置 */
    public WimoCapability getByKey(String key)
    {
        if (key == null) return null;
        return safe(() -> capabilityMapper.selectByKey(key), null);
    }

    public WimoCapability getById(Long id) { return safe(() -> capabilityMapper.selectById(id), null); }

    public List<WimoPriceNode> allNodes() { return safe(() -> nodeMapper.selectAll(), List.of()); }

    public List<WimoPriceNode> nodesOf(String capabilityKey)
    {
        return safe(() -> nodeMapper.selectByCapability(capabilityKey), List.of());
    }

    /** 某能力 + 某维度下所有启用档位（计费引擎逐层求值用） */
    public List<WimoPriceNode> enabledOptions(String capabilityKey, String dimension)
    {
        return safe(() -> nodeMapper.selectEnabledByDimension(capabilityKey, dimension), List.of());
    }

    /** 某能力下全部启用档位（仅 option 类型，计费引擎按维度分组求值用）；无表/无节点返回空以触发旧配置回退 */
    public List<WimoPriceNode> enabledNodes(String capabilityKey)
    {
        List<WimoPriceNode> all = safe(() -> nodeMapper.selectEnabledByCapability(capabilityKey), List.of());
        List<WimoPriceNode> out = new ArrayList<>();
        for (WimoPriceNode n : all)
        {
            if ("option".equals(n.getNodeType())) out.add(n);
        }
        return out;
    }

    /**
     * 组装四级计费规则树（管理端 / 用户端共用）。
     * 每个能力节点含：基础计费参数 + dimensions(分组→档位) + models + workflows。
     */
    public List<Map<String, Object>> tree()
    {
        List<WimoCapability> caps = listAll();
        if (caps.isEmpty()) return List.of();
        List<WimoPriceNode> nodes = allNodes();
        Map<String, List<WimoPriceNode>> byCap = nodes.stream()
                .collect(Collectors.groupingBy(WimoPriceNode::getCapabilityKey, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<com.ruoyi.wimo.domain.WimoModel>> modelsByCap = modelMapper.selectAll().stream()
                .collect(Collectors.groupingBy(com.ruoyi.wimo.domain.WimoModel::getCapability, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<com.ruoyi.wimo.domain.WimoWorkflow>> wfByCap = workflowMapper.selectAll().stream()
                .collect(Collectors.groupingBy(com.ruoyi.wimo.domain.WimoWorkflow::getCapability, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (WimoCapability c : caps)
        {
            Map<String, Object> cap = new LinkedHashMap<>();
            cap.put("capabilityKey", c.getCapabilityKey());
            cap.put("nameZh", c.getNameZh());
            cap.put("nameEn", c.getNameEn());
            cap.put("icon", c.getIcon());
            cap.put("baseCost", c.getBaseCost());
            cap.put("unit", c.getUnit());
            cap.put("unitCost", c.getUnitCost());
            cap.put("unitField", c.getUnitField());
            cap.put("minCost", c.getMinCost());
            cap.put("maxCost", c.getMaxCost());
            cap.put("defaultModel", c.getDefaultModel());
            cap.put("enabled", c.getEnabled());
            cap.put("sortNo", c.getSortNo());

            // 维度分组 → 档位
            List<WimoPriceNode> capNodes = byCap.getOrDefault(c.getCapabilityKey(), List.of());
            Map<String, WimoPriceNode> groups = new LinkedHashMap<>();
            List<WimoPriceNode> orphans = new ArrayList<>();
            for (WimoPriceNode n : capNodes)
            {
                if ("group".equals(n.getNodeType())) groups.put(n.getDimension(), n);
                else orphans.add(n);
            }
            Map<String, Map<String, Object>> dimMap = new LinkedHashMap<>();
            for (Map.Entry<String, WimoPriceNode> ge : groups.entrySet())
            {
                String dim = ge.getKey();
                WimoPriceNode g = ge.getValue();
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("dimension", dim);
                dm.put("nodeId", g.getNodeId());
                dm.put("labelZh", g.getLabelZh());
                dm.put("labelEn", g.getLabelEn());
                dm.put("enabled", g.getEnabled());
                dm.put("options", new ArrayList<Map<String, Object>>());
                dimMap.put(dim, dm);
            }
            for (WimoPriceNode n : orphans)
            {
                Map<String, Object> dm = dimMap.computeIfAbsent(n.getDimension(), k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    String[] label = DIM_LABEL.getOrDefault(k, new String[] { k, k });
                    m.put("dimension", k); m.put("labelZh", label[0]); m.put("labelEn", label[1]);
                    m.put("enabled", "1"); m.put("options", new ArrayList<Map<String, Object>>());
                    return m;
                });
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> opts = (List<Map<String, Object>>) dm.get("options");
                opts.add(nodeMap(n));
            }
            cap.put("dimensions", new ArrayList<>(dimMap.values()));

            // 模型分支
            List<Map<String, Object>> models = new ArrayList<>();
            for (com.ruoyi.wimo.domain.WimoModel m : modelsByCap.getOrDefault(c.getCapabilityKey(), List.of()))
            {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("modelKey", m.getModelKey());
                mm.put("nameZh", m.getNameZh());
                mm.put("nameEn", m.getNameEn());
                mm.put("costMultiplier", m.getCostMultiplier());
                mm.put("enabled", m.getEnabled());
                models.add(mm);
            }
            cap.put("models", models);

            // 工作流分支
            List<Map<String, Object>> wfs = new ArrayList<>();
            for (com.ruoyi.wimo.domain.WimoWorkflow w : wfByCap.getOrDefault(c.getCapabilityKey(), List.of()))
            {
                Map<String, Object> wm = new LinkedHashMap<>();
                wm.put("workflowKey", w.getWorkflowKey());
                wm.put("nameZh", w.getNameZh());
                wm.put("nameEn", w.getNameEn());
                wm.put("baseCost", w.getBaseCost());
                wm.put("unitCost", w.getUnitCost());
                wm.put("costSource", w.getCostSource());
                wm.put("defaultModel", w.getDefaultModel());
                wm.put("enabled", w.getEnabled());
                wfs.add(wm);
            }
            cap.put("workflows", wfs);
            out.add(cap);
        }
        return out;
    }

    private Map<String, Object> nodeMap(WimoPriceNode n)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodeId", n.getNodeId());
        m.put("nodeKey", n.getNodeKey());
        m.put("dimension", n.getDimension());
        m.put("optionValue", n.getOptionValue());
        m.put("labelZh", n.getLabelZh());
        m.put("labelEn", n.getLabelEn());
        m.put("factor", n.getFactor());
        m.put("unitCost", n.getUnitCost());
        m.put("minValue", n.getMinValue());
        m.put("maxValue", n.getMaxValue());
        m.put("enabled", n.getEnabled());
        m.put("sortNo", n.getSortNo());
        return m;
    }

    /** 用户端精简树：按能力返回可选档位（不暴露内部 ID / 备注） */
    public List<Map<String, Object>> publicTree()
    {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> cap : tree())
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("capabilityKey", cap.get("capabilityKey"));
            m.put("nameZh", cap.get("nameZh"));
            m.put("nameEn", cap.get("nameEn"));
            m.put("baseCost", cap.get("baseCost"));
            m.put("unit", cap.get("unit"));
            m.put("unitCost", cap.get("unitCost"));
            m.put("unitField", cap.get("unitField"));
            m.put("minCost", cap.get("minCost"));
            m.put("maxCost", cap.get("maxCost"));
            m.put("defaultModel", cap.get("defaultModel"));
            m.put("dimensions", cap.get("dimensions"));
            m.put("models", cap.get("models"));
            out.add(m);
        }
        return out;
    }

    /** 保存能力（改基础价即时生效） */
    public int save(WimoCapability c)
    {
        if (c == null || c.getCapabilityId() == null) throw new WimoException("能力不存在");
        WimoCapability cur = capabilityMapper.selectById(c.getCapabilityId());
        if (cur == null) throw new WimoException("能力不存在");
        c.setCapabilityKey(cur.getCapabilityKey()); // key 不可改
        c.setCreateTime(cur.getCreateTime());
        if (c.getBaseCost() == null) c.setBaseCost(cur.getBaseCost());
        if (c.getUnitCost() == null) c.setUnitCost(cur.getUnitCost());
        if (c.getMinCost() == null) c.setMinCost(cur.getMinCost());
        if (c.getMaxCost() == null) c.setMaxCost(cur.getMaxCost());
        if (c.getMaxCost() < c.getMinCost()) throw new WimoException("最高消耗不能低于最低消耗");
        c.setUpdateTime(new Date());
        return capabilityMapper.updateCapability(c);
    }

    /** 手动重载（JSON 变更或清库后重新灌入，不清空已有数据） */
    public Map<String, Object> reload()
    {
        initCapabilities();
        initPriceNodes();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("capabilities", safe(() -> capabilityMapper.countAll(), 0));
        m.put("priceNodes", safe(() -> nodeMapper.countAll(), 0));
        return m;
    }

    /** 老库未建能力表/价格节点表时静默降级，保证计费与页面不 500 */
    private <T> T safe(Supplier<T> supplier, T fallback)
    {
        try { return supplier.get(); }
        catch (Exception e) { return fallback; }
    }

    static String str(Object o) { return o == null ? "" : o.toString(); }
    static int intVal(Object o, int d) { try { return o == null ? d : Integer.parseInt(o.toString()); } catch (Exception e) { return d; } }
    static long longVal(Object o, long d) { try { return o == null ? d : Long.parseLong(o.toString()); } catch (Exception e) { return d; } }
    static BigDecimal dec(Object o, BigDecimal d) { try { return o == null ? d : new BigDecimal(o.toString()); } catch (Exception e) { return d; } }
}
