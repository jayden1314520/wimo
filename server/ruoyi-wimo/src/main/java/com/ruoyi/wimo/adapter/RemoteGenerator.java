package com.ruoyi.wimo.adapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.ruoyi.wimo.config.WimoProperties;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.exception.WimoException;

/**
 * 真实生成服务适配器：把任务参数 POST 到模型网关，解析 { "results": ["url", ...] }。
 *
 * <pre>
 * wimo:
 *   generator:
 *     provider: remote
 *     remote-url: https://api.example.com/v1/generate
 *     remote-token: your-api-token
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "wimo.generator.provider", havingValue = "remote")
public class RemoteGenerator implements WimoGenerator
{
    private static final Logger log = LoggerFactory.getLogger(RemoteGenerator.class);

    @Autowired
    private WimoProperties props;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<String> generate(WimoTask task) throws Exception
    {
        String endpoint = props.getGeneratorRemoteUrl();
        if (endpoint == null || endpoint.isEmpty())
        {
            throw new WimoException("已启用真实生成服务但未配置 wimo.generator.remote-url");
        }
        String body = objectMapper.writeValueAsString(task);
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getGeneratorRemoteToken())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300)
        {
            throw new WimoException("生成服务返回 " + resp.statusCode() + "：" + resp.body());
        }
        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode results = root.path("results");
        List<String> urls = new ArrayList<>();
        if (results.isArray())
        {
            results.forEach(n -> urls.add(n.asText()));
        }
        else if (root.has("url"))
        {
            urls.add(root.path("url").asText());
        }
        if (urls.isEmpty())
        {
            throw new WimoException("生成服务未返回结果文件");
        }
        log.info("[REMOTE GEN] taskId={} results={}", task.getTaskId(), urls.size());
        return urls;
    }

    @Override
    public String provider()
    {
        return "remote";
    }

    @Override
    public long stepMs()
    {
        return props.getGeneratorStepMs();
    }
}
