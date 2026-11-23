package com.ruoyi.wimo.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.service.IWimoTaskService;

/**
 * 管理端 · 套图任务管理
 *
 * <p>走若依权限体系（登录 + @PreAuthorize），与用户端匿名接口分离。</p>
 *
 * @author wimo
 */
@RestController
@RequestMapping("/wimo/task")
public class WimoTaskAdminController extends BaseController
{
    @Autowired
    private IWimoTaskService wimoTaskService;

    /**
     * 分页查询套图任务
     */
    @PreAuthorize("@ss.hasPermi('wimo:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(WimoTask query)
    {
        startPage();
        List<WimoTask> list = wimoTaskService.listTasks(query);
        return getDataTable(list);
    }

    /**
     * 任务详情
     */
    @PreAuthorize("@ss.hasPermi('wimo:task:query')")
    @GetMapping("/{taskId}")
    public AjaxResult getInfo(@PathVariable("taskId") Long taskId)
    {
        return success(wimoTaskService.getTask(taskId));
    }

    /**
     * 删除任务
     */
    @PreAuthorize("@ss.hasPermi('wimo:task:remove')")
    @DeleteMapping("/{taskIds}")
    public AjaxResult remove(@PathVariable Long[] taskIds)
    {
        return toAjax(wimoTaskService.deleteTasks(taskIds));
    }
}
