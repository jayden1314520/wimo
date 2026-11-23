package com.ruoyi.wimo.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.wimo.adapter.WimoGenerator;
import com.ruoyi.wimo.adapter.WimoMailSender;
import com.ruoyi.wimo.adapter.WimoStorage;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.mapper.WimoMailLogMapper;
import com.ruoyi.wimo.mapper.WimoSchemaMapper;
import com.ruoyi.wimo.mapper.WimoTaskMapper;
import com.ruoyi.wimo.mapper.WimoUserMapper;

/** 管理端数据看板：总览指标、状态/功能分布、趋势、最近任务、适配器状态 */
@Service
public class WimoDashboardService
{
    @Autowired private WimoTaskMapper taskMapper;
    @Autowired private WimoUserMapper userMapper;
    @Autowired private WimoSchemaMapper schemaMapper;
    @Autowired private WimoMailLogMapper mailLogMapper;
    @Autowired private IWimoTaskService taskService;
    @Autowired private WimoGenerator generator;
    @Autowired private WimoMailSender mailSender;
    @Autowired private WimoStorage storage;

    public Map<String, Object> full(int days)
    {
        Map<String, Object> out = new LinkedHashMap<>();

        Map<String, Object> ov = taskMapper.overview();
        if (ov == null)
        {
            ov = new LinkedHashMap<>();
        }
        ov.put("users", userMapper.countAll());
        ov.put("schemas", schemaMapper.countAll());
        ov.put("mailLogs", mailLogMapper.countAll());

        Map<String, Object> statusMap = new LinkedHashMap<>();
        statusMap.put("0", 0);
        statusMap.put("1", 0);
        statusMap.put("2", 0);
        statusMap.put("3", 0);
        statusMap.put("4", 0);
        for (Map<String, Object> row : taskMapper.countByStatus())
        {
            statusMap.put(String.valueOf(row.get("status")), row.get("cnt"));
        }
        ov.put("statusMap", statusMap);

        Map<String, Object> providers = new LinkedHashMap<>();
        providers.put("generator", generator.provider());
        providers.put("mail", mailSender.provider());
        providers.put("storage", storage.provider());
        out.put("providers", providers);

        out.put("overview", ov);
        out.put("byType", taskMapper.countByType());
        out.put("trend", taskMapper.trendByDay(Math.max(1, Math.min(90, days))));

        WimoTask q = new WimoTask();
        q.setStatus("");
        List<WimoTask> recent = taskMapper.selectTaskList(q);
        out.put("recent", recent.size() > 10 ? recent.subList(0, 10) : recent);
        return out;
    }
}
