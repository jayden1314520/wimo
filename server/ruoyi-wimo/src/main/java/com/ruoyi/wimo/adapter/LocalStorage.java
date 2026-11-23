package com.ruoyi.wimo.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.ruoyi.common.config.RuoYiConfig;

/** 本地磁盘存储：按用户 + 日期隔离，URL 由若依 /profile/** 静态映射对外提供 */
@Component
@ConditionalOnProperty(name = "wimo.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorage implements WimoStorage
{
    private static final Set<String> ALLOWED = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".mp4", ".mov", ".webm");

    @Override
    public String store(byte[] data, String originalName, Long userId) throws Exception
    {
        String ext = ".jpg";
        if (originalName != null && originalName.contains("."))
        {
            String e = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (ALLOWED.contains(e))
            {
                ext = e;
            }
        }
        String day = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        String owner = "u" + (userId == null ? 0L : userId);
        Path dir = Path.of(RuoYiConfig.getProfile(), "upload", day, owner);
        Files.createDirectories(dir);
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        Files.write(dir.resolve(name), data);
        return "/profile/upload/" + day + "/" + owner + "/" + name;
    }

    @Override
    public String provider()
    {
        return "local";
    }
}
