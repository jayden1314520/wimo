package com.ruoyi.wimo.controller;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.service.IWimoTaskService;

/**
 * 用户端 · AI 套图任务接口
 *
 * <p>面向 Nuxt 用户端，全部标记 @Anonymous 免登录（后续接入会员体系可改为 token 鉴权）。</p>
 *
 * @author wimo
 */
@Anonymous
@RestController
@RequestMapping("/api/wimo/task")
public class WimoTaskController
{
    @Autowired
    private IWimoTaskService wimoTaskService;

    /**
     * 创建套图任务
     */
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody WimoTask task)
    {
        if (StringUtils.isEmpty(task.getSellingPoints()))
        {
            return R.fail("商品卖点 & 要求不能为空");
        }
        WimoTask created = wimoTaskService.createTask(task);
        Map<String, Object> data = new HashMap<>(4);
        data.put("taskId", created.getTaskId());
        data.put("status", created.getStatus());
        return R.ok(data);
    }

    /**
     * 查询任务状态（前端轮询）
     */
    @GetMapping("/{taskId}")
    public R<WimoTask> get(@PathVariable("taskId") Long taskId)
    {
        WimoTask task = wimoTaskService.getTask(taskId);
        if (task == null)
        {
            return R.fail("任务不存在");
        }
        return R.ok(task);
    }

    /**
     * 最近生成记录
     */
    @GetMapping("/list")
    public R<List<WimoTask>> list(@RequestParam(value = "limit", defaultValue = "10") int limit)
    {
        return R.ok(wimoTaskService.listTasks(limit));
    }

    /**
     * 素材上传
     */
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException
    {
        if (file == null || file.isEmpty())
        {
            return R.fail("文件不能为空");
        }
        String original = file.getOriginalFilename();
        String ext = original != null && original.contains(".")
                ? original.substring(original.lastIndexOf('.')) : ".jpg";
        String day = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;

        String dir = RuoYiConfig.getProfile() + "/upload/" + day;
        File target = new File(dir, fileName);
        if (!target.getParentFile().exists() && !target.getParentFile().mkdirs())
        {
            return R.fail("创建上传目录失败");
        }
        file.transferTo(target);

        Map<String, Object> data = new HashMap<>(4);
        data.put("url", "/profile/upload/" + day + "/" + fileName);
        data.put("name", original);
        return R.ok(data);
    }
}
