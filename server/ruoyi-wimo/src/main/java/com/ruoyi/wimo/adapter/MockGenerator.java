package com.ruoyi.wimo.adapter;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.wimo.config.WimoProperties;
import com.ruoyi.wimo.domain.WimoTask;

/**
 * 本地模拟生成器：把示例封面/示例视频复制为真实结果文件（或生成占位图），
 * 结果落到 RuoYi profile/result 下并通过 /profile/** 对外可访问。
 * 配置 wimo.generator.provider=remote 后切换为 RemoteGenerator。
 */
@Component
@ConditionalOnProperty(name = "wimo.generator.provider", havingValue = "mock", matchIfMissing = true)
public class MockGenerator implements WimoGenerator
{
    private static final Logger log = LoggerFactory.getLogger(MockGenerator.class);

    @Autowired
    private WimoProperties props;

    @Override
    public List<String> generate(WimoTask task) throws Exception
    {
        String day = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        Path dir = Path.of(RuoYiConfig.getProfile(), "result", day);
        Files.createDirectories(dir);

        File sampleDir = new File(props.getSampleDir());
        List<String> urls = new ArrayList<>();

        // 视频类功能优先产出示例 mp4
        File clip = new File(sampleDir, "clip-" + task.getTaskType() + ".mp4");
        if (clip.isFile())
        {
            String name = UUID.randomUUID().toString().replace("-", "") + ".mp4";
            Files.copy(clip.toPath(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            urls.add("/profile/result/" + day + "/" + name);
            return urls;
        }

        String[] candidates = {
                "cover-" + task.getTaskType() + ".jpg",
                "poster-" + task.getTaskType() + ".jpg",
                "cover-productSet.jpg", "cover-scene.jpg", "cover-poster.jpg"
        };
        int count = Math.max(1, props.getGeneratorResultCount());
        for (int i = 0; i < count; i++)
        {
            String name = UUID.randomUUID().toString().replace("-", "") + ".jpg";
            Path target = dir.resolve(name);
            File src = null;
            for (String c : candidates)
            {
                File f = new File(sampleDir, c);
                if (f.isFile())
                {
                    src = f;
                    break;
                }
            }
            if (src != null)
            {
                Files.copy(src.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            }
            else
            {
                drawPlaceholder(target, task, i + 1);
            }
            urls.add("/profile/result/" + day + "/" + name);
        }
        log.info("[MOCK GEN] taskId={} type={} results={}", task.getTaskId(), task.getTaskType(), urls.size());
        return urls;
    }

    /** 无示例素材时生成一张带任务信息的占位图，保证结果文件真实存在 */
    private void drawPlaceholder(Path target, WimoTask task, int index) throws Exception
    {
        BufferedImage img = new BufferedImage(640, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(244, 244, 247));
        g.fillRect(0, 0, 640, 800);
        g.setColor(new Color(124, 108, 245));
        g.fillRect(0, 0, 640, 12);
        g.setColor(new Color(24, 24, 27));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        g.drawString("VIVID RESULT", 40, 90);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        g.drawString("task #" + task.getTaskId() + "  " + String.valueOf(task.getTaskType()), 40, 130);
        g.drawString("index " + index, 40, 160);
        g.setColor(new Color(230, 230, 236));
        g.fillRoundRect(40, 200, 560, 540, 18, 18);
        g.setColor(new Color(160, 160, 172));
        g.drawString("Mock generated asset", 220, 480);
        g.dispose();
        ImageIO.write(img, "jpg", target.toFile());
    }

    @Override
    public String provider()
    {
        return "mock";
    }

    @Override
    public long stepMs()
    {
        return props.getGeneratorStepMs();
    }
}
