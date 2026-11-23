package com.ruoyi.wimo.service;

import java.io.InputStream;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wimo.domain.WimoSchema;
import com.ruoyi.wimo.domain.dto.WimoTaskSubmit;
import com.ruoyi.wimo.exception.WimoException;
import com.ruoyi.wimo.mapper.WimoSchemaMapper;

/**
 * 功能模板服务：启动时把 13 个功能 schema 同步到 DB（不覆盖管理端已有修改），
 * 并在任务提交时按 schema 做后端校验（上传数量、必填/选填、选项、生成设置）。
 */
@Service
public class WimoSchemaService
{
    private static final Logger log = LoggerFactory.getLogger(WimoSchemaService.class);

    @Autowired
    private WimoSchemaMapper schemaMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init()
    {
        try
        {
            ClassPathResource res = new ClassPathResource("wimo/schemas.json");
            if (!res.exists())
            {
                log.warn("wimo/schemas.json 不存在，跳过模板初始化");
                return;
            }
            List<Map<String, Object>> defs;
            try (InputStream in = res.getInputStream())
            {
                defs = objectMapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {});
            }
            int added = 0;
            for (Map<String, Object> d : defs)
            {
                String key = str(d.get("key"));
                if (key.isEmpty() || schemaMapper.selectByKey(key) != null)
                {
                    continue;
                }
                WimoSchema s = new WimoSchema();
                s.setTaskKey(key);
                s.setNameZh(str(d.get("nameZh")));
                s.setNameEn(str(d.get("nameEn")));
                s.setGroupKey(str(d.getOrDefault("group", "ecommerce")));
                s.setIcon(str(d.get("icon")));
                s.setSortNo(intVal(d.get("sortNo"), 0));
                s.setEnabled("1");
                s.setSchemaJson(objectMapper.writeValueAsString(d));
                s.setRemark("系统内置模板");
                s.setCreateTime(new Date());
                s.setUpdateTime(s.getCreateTime());
                schemaMapper.insertSchema(s);
                added++;
            }
            log.info("映物功能模板初始化完成，新增 {} 个，当前共 {} 个", added, schemaMapper.countAll());
        }
        catch (Exception e)
        {
            log.error("映物功能模板初始化失败", e);
        }
    }

    public List<WimoSchema> listEnabled()
    {
        return schemaMapper.selectEnabled();
    }

    public List<WimoSchema> listAll()
    {
        return schemaMapper.selectAll();
    }

    public WimoSchema getById(Long id)
    {
        for (WimoSchema s : schemaMapper.selectAll())
        {
            if (s.getId().equals(id))
            {
                return s;
            }
        }
        return null;
    }

    public int update(WimoSchema schema)
    {
        schema.setUpdateTime(new Date());
        return schemaMapper.updateSchema(schema);
    }

    /** 用户端可见的 schema（剥离管理端字段） */
    public List<Map<String, Object>> publicSchemas()
    {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WimoSchema s : listEnabled())
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", s.getTaskKey());
            m.put("nameZh", s.getNameZh());
            m.put("nameEn", s.getNameEn());
            m.put("group", s.getGroupKey());
            m.put("icon", s.getIcon());
            m.put("sortNo", s.getSortNo());
            try
            {
                // 必须转成普通 Map：fastjson2 会把 Jackson JsonNode 当 POJO 序列化
                m.put("schema", objectMapper.readValue(s.getSchemaJson(),
                        new TypeReference<Map<String, Object>>() {}));
            }
            catch (Exception e)
            {
                m.put("schema", null);
            }
            out.add(m);
        }
        return out;
    }

    public JsonNode getSchema(String taskKey)
    {
        WimoSchema s = schemaMapper.selectByKey(taskKey);
        if (s == null)
        {
            throw new WimoException("功能不存在：" + taskKey);
        }
        if (!"1".equals(s.getEnabled()))
        {
            throw new WimoException("该功能已停用，请选择其他功能");
        }
        try
        {
            return objectMapper.readTree(s.getSchemaJson());
        }
        catch (Exception e)
        {
            throw new WimoException("模板配置解析失败：" + taskKey);
        }
    }

    /** 按模板校验提交载荷，失败抛 WimoException */
    public void validate(String taskKey, WimoTaskSubmit submit)
    {
        if (submit == null || taskKey == null || taskKey.isEmpty())
        {
            throw new WimoException("缺少任务类型");
        }
        JsonNode schema = getSchema(taskKey);
        Map<String, List<String>> uploads = submit.getUploads() == null ? Map.of() : submit.getUploads();
        Map<String, Object> values = submit.getValues() == null ? Map.of() : submit.getValues();
        Map<String, Object> common = submit.getCommon() == null ? Map.of() : submit.getCommon();

        // 1. 上传槽位：数量区间 + URL 合法性
        for (JsonNode slot : schema.path("uploads"))
        {
            String key = slot.path("key").asText();
            if (slot.path("optional").asBoolean(false))
            {
                continue;
            }
            int min = slot.path("min").asInt(1);
            int max = slot.path("max").asInt(1);
            List<String> files = uploads.get(key);
            int n = files == null ? 0 : files.size();
            if (n < min || n > max)
            {
                throw new WimoException("「" + key + "」需上传 " + min + "–" + max + " 个素材，当前 " + n + " 个");
            }
            for (String url : files)
            {
                if (!safeUrl(url))
                {
                    throw new WimoException("素材地址不合法：" + url);
                }
            }
        }

        // 2. 特有字段：必填 / 选项 / 数字范围
        for (JsonNode f : schema.path("fields"))
        {
            String key = f.path("key").asText();
            String type = f.path("type").asText();
            Object v = values.get(key);
            boolean blank = v == null || v.toString().trim().isEmpty();
            if (f.path("required").asBoolean(false) && blank)
            {
                throw new WimoException("「" + key + "」为必填项");
            }
            if (blank)
            {
                continue;
            }
            if ("select".equals(type) && f.has("options"))
            {
                boolean hit = false;
                for (JsonNode opt : f.path("options"))
                {
                    if (opt.asText().equals(v.toString()))
                    {
                        hit = true;
                        break;
                    }
                }
                if (!hit)
                {
                    throw new WimoException("「" + key + "」的取值不在允许范围内");
                }
            }
            if ("number".equals(type))
            {
                int num;
                try
                {
                    num = Integer.parseInt(v.toString());
                }
                catch (NumberFormatException e)
                {
                    throw new WimoException("「" + key + "」必须是数字");
                }
                if (num < 1 || num > 20)
                {
                    throw new WimoException("「" + key + "」需在 1–20 之间");
                }
            }
        }

        // 3. 生成设置：模板声明的配置项必须齐全
        for (JsonNode c : schema.path("common"))
        {
            String key = c.asText();
            Object v = common.get(key);
            if (v == null || v.toString().trim().isEmpty())
            {
                throw new WimoException("生成设置缺少「" + key + "」");
            }
        }
    }

    private boolean safeUrl(String url)
    {
        if (url == null)
        {
            return false;
        }
        String u = url.trim();
        return u.startsWith("/profile/") || u.startsWith("/images/")
                || u.startsWith("https://") || u.startsWith("http://");
    }

    private String str(Object o)
    {
        return o == null ? "" : o.toString();
    }

    private int intVal(Object o, int def)
    {
        try
        {
            return Integer.parseInt(o.toString());
        }
        catch (Exception e)
        {
            return def;
        }
    }
}
