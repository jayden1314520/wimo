package com.ruoyi.wimo.controller;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.mapper.WimoTaskMapper;
import com.ruoyi.wimo.service.IWimoTaskService;

/** 管理端 · 生成任务管理（查询 / 详情 / 删除 / 重试 / 取消） */
@RestController
@RequestMapping("/wimo/task")
public class WimoTaskAdminController extends BaseController
{
    @Autowired
    private IWimoTaskService wimoTaskService;

    @Autowired
    private WimoTaskMapper taskMapper;

    @PreAuthorize("@ss.hasPermi('wimo:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(WimoTask query)
    {
        startPage();
        List<WimoTask> list = wimoTaskService.listTasks(query);
        return getDataTable(list);
    }

    /** 详情：附带结果文件明细 */
    @PreAuthorize("@ss.hasPermi('wimo:task:query')")
    @GetMapping("/{taskId}")
    public AjaxResult getInfo(@PathVariable("taskId") Long taskId)
    {
        return success(wimoTaskService.getTaskDetail(taskId));
    }

    @PreAuthorize("@ss.hasPermi('wimo:task:remove')")
    @DeleteMapping("/{taskIds}")
    public AjaxResult remove(@PathVariable Long[] taskIds)
    {
        return toAjax(wimoTaskService.deleteTasks(taskIds));
    }

    /** 重试失败/已完成/已取消任务（管理端不受归属限制） */
    @PreAuthorize("@ss.hasPermi('wimo:task:retry')")
    @PostMapping("/retry/{taskId}")
    public AjaxResult retry(@PathVariable("taskId") Long taskId)
    {
        return success(wimoTaskService.retryTask(taskId, 0L));
    }

    /** 取消生成中任务 */
    @PreAuthorize("@ss.hasPermi('wimo:task:retry')")
    @PostMapping("/cancel/{taskId}")
    public AjaxResult cancel(@PathVariable("taskId") Long taskId)
    {
        return success(wimoTaskService.cancelTask(taskId, 0L));
    }

    /** 直接改状态（运维用） */
    @PreAuthorize("@ss.hasPermi('wimo:task:retry')")
    @PostMapping("/status/{taskId}/{status}")
    public AjaxResult status(@PathVariable("taskId") Long taskId, @PathVariable("status") String status)
    {
        WimoTask t = new WimoTask();
        t.setTaskId(taskId);
        t.setStatus(status);
        t.setUpdateTime(new Date());
        return toAjax(taskMapper.updateTask(t));
    }
}
