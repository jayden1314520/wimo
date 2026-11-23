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
import com.ruoyi.wimo.domain.WimoModel;
import com.ruoyi.wimo.domain.WimoWorkflow;
import com.ruoyi.wimo.exception.WimoException;
import com.ruoyi.wimo.mapper.WimoModelMapper;

/**
 * AI 模型服务：管理端配置四类能力模型，用户端按工作流能力动态拉取可选列表。
 * 启动时从 wimo/models.json 初始化默认模型，不覆盖管理端已有修改。
 *
 * @author wimo
 */
@Service
public class WimoModelService
{
    private static final Logger log = LoggerFactory.getLogger(WimoModelService.class);

    @Autowired private WimoModelMapper modelMapper;
    @Autowired private ObjectMapper objectMapper;

    @PostConstruct
    public void init()
    {
        try
        {
            ClassPathResource res = new ClassPathResource("wimo/models.json");
            if (!res.exists()) { log.warn("wimo/models.json 不存在，跳过模型初始化"); return; }
            List<Map<String, Object>> defs;
            try (InputStream in = res.getInputStream())
            {
                defs = objectMapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {});
            }
            int added = 0;
            for (Map<String, Object> d : defs)
            {
                String key = str(d.get("modelKey"));
                if (key.isEmpty() || modelMapper.selectByKey(key) != null) continue;
                WimoModel m = new WimoModel();
                m.setModelKey(key);
                m.setNameZh(str(d.get("nameZh")));
                m.setNameEn(str(d.get("nameEn")));
                m.setCapability(str(d.getOrDefault("capability", "img2img")));
                m.setProvider(str(d.getOrDefault("provider", "mock")));
                m.setEndpoint(str(d.get("endpoint")));
                m.setCredentialRef(str(d.get("credentialRef")));
                m.setCostMultiplier(dec(d.get("costMultiplier"), BigDecimal.ONE));
                m.setConfigJson(str(d.getOrDefault("configJson", "{}")));
                m.setEnabled("1");
                m.setSortNo(intVal(d.get("sortNo"), 0));
                m.setRemark(str(d.get("remark")));
                m.setCreateTime(new Date());
                m.setUpdateTime(m.getCreateTime());
                modelMapper.insertModel(m);
                added++;
            }
            log.info("映物 AI 模型初始化完成，新增 {} 个，当前共 {} 个", added, modelMapper.countAll());
        }
        catch (Exception e)
        {
            log.error("AI 模型初始化失败", e);
        }
    }

    /** 管理端列表 */
    public List<WimoModel> listAll() { return modelMapper.selectAll(); }

    public WimoModel getById(Long id) { return modelMapper.selectById(id); }

    public WimoModel getByKey(String key) { return key == null ? null : modelMapper.selectByKey(key); }

    /** 某智能体可用的模型：能力匹配 + 启用 + allowedModels 白名单 */
    public List<WimoModel> modelsFor(WimoWorkflow wf)
    {
        String cap = wf == null ? "img2img" : wf.getCapability();
        List<WimoModel> all = modelMapper.selectEnabledByCapability(cap);
        String allow = wf == null ? "" : nz(wf.getAllowedModels());
        if (allow.isEmpty()) return all;
        List<String> keys = java.util.Arrays.stream(allow.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<WimoModel> out = new ArrayList<>();
        for (WimoModel m : all) if (keys.contains(m.getModelKey())) out.add(m);
        return out;
    }

    /** 用户端模型列表（精简字段，避免泄漏 endpoint/密钥引用） */
    public List<Map<String, Object>> publicModels(String capability, String workflowKey, WimoWorkflowService workflowService)
    {
        WimoWorkflow wf = workflowKey == null || workflowKey.isEmpty() ? null : workflowService.getByKey(workflowKey);
        List<WimoModel> src = wf != null ? modelsFor(wf)
                : (capability == null || capability.isEmpty() ? modelMapper.selectAll() : modelMapper.selectEnabledByCapability(capability));
        List<Map<String, Object>> out = new ArrayList<>();
        for (WimoModel m : src)
        {
            if (!"1".equals(m.getEnabled())) continue;
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("modelKey", m.getModelKey());
            x.put("nameZh", m.getNameZh());
            x.put("nameEn", m.getNameEn());
            x.put("capability", m.getCapability());
            x.put("costMultiplier", m.getCostMultiplier());
            x.put("config", parseJson(m.getConfigJson()));
            out.add(x);
        }
        return out;
    }

    public int save(WimoModel m)
    {
        if (m.getModelKey() == null || m.getModelKey().isBlank()) throw new WimoException("模型标识不能为空");
        WimoModel exist = modelMapper.selectByKey(m.getModelKey());
        if (exist != null && !exist.getModelId().equals(m.getModelId()))
        {
            throw new WimoException("模型标识已存在：" + m.getModelKey());
        }
        m.setUpdateTime(new Date());
        if (m.getCostMultiplier() == null) m.setCostMultiplier(BigDecimal.ONE);
        if (m.getEnabled() == null) m.setEnabled("1");
        if (m.getModelId() == null)
        {
            m.setCreateTime(m.getUpdateTime());
            return modelMapper.insertModel(m);
        }
        return modelMapper.updateModel(m);
    }

    public int remove(Long id) { return modelMapper.deleteById(id); }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json)
    {
        try { return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    static String str(Object o) { return o == null ? "" : o.toString(); }
    static int intVal(Object o, int d) { try { return o == null ? d : Integer.parseInt(o.toString()); } catch (Exception e) { return d; } }
    static BigDecimal dec(Object o, BigDecimal d) { try { return o == null ? d : new BigDecimal(o.toString()); } catch (Exception e) { return d; } }
    static String nz(String s) { return s == null ? "" : s; }
}
