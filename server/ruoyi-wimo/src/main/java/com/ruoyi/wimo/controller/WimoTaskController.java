package com.ruoyi.wimo.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.wimo.adapter.WimoGenerator;
import com.ruoyi.wimo.adapter.WimoMailSender;
import com.ruoyi.wimo.adapter.WimoStorage;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.domain.dto.WimoTaskSubmit;
import com.ruoyi.wimo.exception.WimoException;
import com.ruoyi.wimo.service.IWimoTaskService;
import com.ruoyi.wimo.service.WimoAuthContext;

/**
 * 用户端 · 生成任务接口
 *
 * <p>创建 / 查询 / 记录 / 上传 / 重试 / 取消 / 删除。
 * 会员令牌可选：登录则任务归属该会员，未登录为匿名演示（userId=0）。</p>
 */
@Anonymous
@RestController
@RequestMapping("/api/wimo/task")
public class WimoTaskController extends WimoApiSupport
{
    private static final long MAX_FILE = 20L * 1024 * 1024;

    @Autowired
    private IWimoTaskService taskService;

    @Autowired
    private WimoStorage storage;

    @Autowired
    private WimoGenerator generator;

    @Autowired
    private WimoMailSender mailSender;

    /** 后端连通性与当前适配器状态，供前端决定是否降级 */
    @GetMapping("/health")
    public R<Map<String, Object>> health()
    {
        return wrap(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ok", true);
            m.put("generator", generator.provider());
            m.put("mail", mailSender.provider());
            m.put("storage", storage.provider());
            return m;
        });
    }

    /** 创建任务（后端按 schema 校验） */
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody WimoTaskSubmit submit)
    {
        return wrap(() -> {
            WimoTask t = taskService.createTask(submit, WimoAuthContext.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", t.getTaskId());
            m.put("status", t.getStatus());
            m.put("taskType", t.getTaskType());
            return m;
        });
    }

    /** 查询任务状态与结果（前端轮询） */
    @GetMapping("/{taskId}")
    public R<WimoTask> get(@PathVariable("taskId") Long taskId)
    {
        return wrap(() -> {
            WimoTask t = taskService.getTaskDetail(taskId);
            if (t == null)
            {
                throw new WimoException("任务不存在");
            }
            Long uid = WimoAuthContext.getUserId();
            if (uid != null && uid > 0)
            {
                if (!uid.equals(t.getUserId()))
                {
                    throw new WimoException("无权查看该任务");
                }
            }
            else if (t.getUserId() != null && t.getUserId() > 0)
            {
                // 未登录只能查看匿名演示任务（userId=0）
                throw new WimoException("请先登录后查看该任务");
            }
            return t;
        });
    }

    /** 我的生成记录（分页） */
    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam(value = "status", required = false) String status,
                                       @RequestParam(value = "page", defaultValue = "1") int page,
                                       @RequestParam(value = "size", defaultValue = "10") int size)
    {
        return wrap(() -> taskService.listMyTasks(WimoAuthContext.getUserId(), status, page, size));
    }

    /** 单素材上传 */
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file)
    {
        return wrap(() -> storeOne(file));
    }

    /** 多素材上传（探店菜品等一次多张） */
    @PostMapping("/uploads")
    public R<List<Map<String, Object>>> uploads(@RequestParam("files") MultipartFile[] files)
    {
        return wrap(() -> {
            if (files == null || files.length == 0)
            {
                throw new WimoException("文件不能为空");
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (MultipartFile f : files)
            {
                out.add(storeOne(f));
            }
            return out;
        });
    }

    /** 失败/已完成任务重试 */
    @PostMapping("/{taskId}/retry")
    public R<WimoTask> retry(@PathVariable("taskId") Long taskId)
    {
        return wrap(() -> taskService.retryTask(taskId, WimoAuthContext.getUserId()));
    }

    /** 取消生成中的任务 */
    @PostMapping("/{taskId}/cancel")
    public R<WimoTask> cancel(@PathVariable("taskId") Long taskId)
    {
        return wrap(() -> taskService.cancelTask(taskId, WimoAuthContext.getUserId()));
    }

    /** 删除自己的任务 */
    @DeleteMapping("/{taskId}")
    public R<Map<String, Object>> delete(@PathVariable("taskId") Long taskId)
    {
        return wrap(() -> Map.of("ok", taskService.deleteMine(taskId, WimoAuthContext.getUserId()) > 0));
    }

    private Map<String, Object> storeOne(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new WimoException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE)
        {
            throw new WimoException("单个文件不能超过 20MB");
        }
        String ct = file.getContentType();
        if (ct != null && !ct.startsWith("image/") && !ct.startsWith("video/"))
        {
            throw new WimoException("仅支持图片或视频素材");
        }
        try
        {
            String url = storage.store(file.getBytes(), file.getOriginalFilename(), WimoAuthContext.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("url", url);
            m.put("name", file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
            m.put("size", file.getSize());
            return m;
        }
        catch (WimoException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new WimoException("文件保存失败：" + e.getMessage());
        }
    }
}
