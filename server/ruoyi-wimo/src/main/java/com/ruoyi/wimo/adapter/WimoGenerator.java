package com.ruoyi.wimo.adapter;

import java.util.List;
import com.ruoyi.wimo.domain.WimoTask;

/** 生成引擎适配器：mock（本地产出示例文件）或 remote（真实生图/生视频服务） */
public interface WimoGenerator
{
    /** 执行生成，返回结果文件 URL 列表 */
    List<String> generate(WimoTask task) throws Exception;

    String provider();

    /** 每步模拟耗时（毫秒），真实服务可返回轮询间隔 */
    long stepMs();
}
