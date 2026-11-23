package com.ruoyi.wimo.service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.wimo.adapter.WimoGenerator;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.domain.WimoTaskResult;
import com.ruoyi.wimo.mapper.WimoTaskMapper;
import com.ruoyi.wimo.mapper.WimoTaskResultMapper;

/**
 * 异步生成工作器：任务创建后提交线程池，按 8 步推进进度，
 * 调用可插拔生成器产出结果文件，最终写入 wimo_task_result。
 * 支持取消（status=4）与失败状态回写。
 */
@Service
public class WimoGenerateWorker
{
    private static final Logger log = LoggerFactory.getLogger(WimoGenerateWorker.class);

    private final AtomicInteger seq = new AtomicInteger(1);

    private final ExecutorService pool = Executors.newFixedThreadPool(4, new ThreadFactory()
    {
        @Override
        public Thread newThread(Runnable r)
        {
            Thread t = new Thread(r, "wimo-gen-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    @Autowired
    private WimoTaskMapper taskMapper;

    @Autowired
    private WimoTaskResultMapper resultMapper;

    @Autowired
    private WimoGenerator generator;

    public void submit(Long taskId)
    {
        pool.execute(() -> run(taskId));
    }

    private void run(Long taskId)
    {
        long start = System.currentTimeMillis();
        WimoTask task = taskMapper.selectTaskById(taskId);
        if (task == null || !"0".equals(task.getStatus()))
        {
            return;
        }
        mark(taskId, "1", 5, null);
        try
        {
            for (int i = 1; i <= 8; i++)
            {
                Thread.sleep(Math.max(50L, generator.stepMs()));
                WimoTask cur = taskMapper.selectTaskById(taskId);
                if (cur == null || "4".equals(cur.getStatus()))
                {
                    log.info("任务已取消 taskId={}", taskId);
                    return;
                }
                WimoTask step = new WimoTask();
                step.setTaskId(taskId);
                step.setProgress(5 + i * 11);
                step.setUpdateTime(new Date());
                taskMapper.updateTask(step);
            }
            List<String> urls = generator.generate(task);
            resultMapper.deleteByTaskId(taskId);
            int sort = 0;
            for (String url : urls)
            {
                WimoTaskResult r = new WimoTaskResult();
                r.setTaskId(taskId);
                r.setFileUrl(url);
                r.setFileType(url.endsWith(".mp4") || url.endsWith(".webm") ? "video" : "image");
                r.setSortNo(sort++);
                r.setCreateTime(new Date());
                resultMapper.insertResult(r);
            }
            WimoTask done = new WimoTask();
            done.setTaskId(taskId);
            done.setStatus("2");
            done.setProgress(100);
            done.setResults(String.join(",", urls));
            done.setResultCount(urls.size());
            done.setCostMs(System.currentTimeMillis() - start);
            done.setFinishTime(new Date());
            done.setUpdateTime(new Date());
            taskMapper.updateTask(done);
            log.info("任务生成完成 taskId={} results={} cost={}ms", taskId, urls.size(), done.getCostMs());
        }
        catch (Exception e)
        {
            log.error("任务生成失败 taskId={}", taskId, e);
            WimoTask fail = new WimoTask();
            fail.setTaskId(taskId);
            fail.setStatus("3");
            fail.setErrorMsg(e.getMessage() == null ? "生成失败" : e.getMessage().substring(0, Math.min(450, e.getMessage().length())));
            fail.setCostMs(System.currentTimeMillis() - start);
            fail.setFinishTime(new Date());
            fail.setUpdateTime(new Date());
            taskMapper.updateTask(fail);
        }
    }

    private void mark(Long taskId, String status, Integer progress, WimoTask extra)
    {
        WimoTask t = extra == null ? new WimoTask() : extra;
        t.setTaskId(taskId);
        t.setStatus(status);
        t.setProgress(progress);
        t.setUpdateTime(new Date());
        taskMapper.updateTask(t);
    }
}
