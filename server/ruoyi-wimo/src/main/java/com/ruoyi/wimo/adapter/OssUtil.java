package com.ruoyi.wimo.adapter;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 对象存储公共工具：签名、摘要、multipart 构造（纯 JDK，无第三方 SDK） */
public final class OssUtil
{
    private OssUtil() {}

    public static String base64Url(byte[] data)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static String base64Url(String s)
    {
        return base64Url(s.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] hmacSha1(String key, byte[] data) throws Exception
    {
        return hmac("HmacSHA1", key.getBytes(StandardCharsets.UTF_8), data);
    }

    public static byte[] hmacSha256(byte[] key, String data) throws Exception
    {
        return hmac("HmacSHA256", key, data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(String alg, byte[] key, byte[] data) throws Exception
    {
        Mac mac = Mac.getInstance(alg);
        mac.init(new SecretKeySpec(key, alg));
        return mac.doFinal(data);
    }

    public static byte[] sha256(byte[] data) throws Exception
    {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    public static String hex(byte[] data)
    {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data)
        {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static String hexSha256(byte[] data) throws Exception
    {
        return hex(sha256(data));
    }

    public static String extOf(String originalName)
    {
        String ext = ".jpg";
        if (originalName != null && originalName.contains("."))
        {
            String e = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (e.matches("\\.[a-z0-9]{1,6}")) ext = e;
        }
        return ext;
    }

    public static String randomKey(String ext)
    {
        return UUID.randomUUID().toString().replace("-", "") + ext;
    }

    /** 构造 multipart/form-data 二进制体（七牛表单上传用） */
    public static byte[] multipart(String boundary, String token, String key,
                                   String fileName, byte[] file) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String crlf = "\r\n";
        field(out, boundary, "token", token, crlf);
        field(out, boundary, "key", key, crlf);
        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + crlf)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: application/octet-stream" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(file);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value, String crlf)
            throws Exception
    {
        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + crlf + crlf)
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write(crlf.getBytes(StandardCharsets.UTF_8));
    }

    public static String joinUrl(String domain, String key)
    {
        String d = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
        String k = key.startsWith("/") ? key.substring(1) : key;
        return d + "/" + k;
    }
}
