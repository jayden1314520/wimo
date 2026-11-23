package com.ruoyi.wimo.domain.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 用户端提交任务的表单载荷（schema 驱动）
 *
 * <p>uploads: 上传槽位 key -> 素材 URL 数组；values: 特有字段；
 * common: 生成设置（ratio/language/videoResolution/duration/videoModel 等）。</p>
 */
@Data
public class WimoTaskSubmit
{
    private String taskType;
    private String title;

    private Map<String, List<String>> uploads = new LinkedHashMap<>();

    private Map<String, Object> values = new LinkedHashMap<>();

    private Map<String, Object> common = new LinkedHashMap<>();

    private String resultMode;

    private String extra;
}
