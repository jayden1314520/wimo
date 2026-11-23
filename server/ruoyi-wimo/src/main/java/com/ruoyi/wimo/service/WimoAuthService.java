package com.ruoyi.wimo.service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.wimo.adapter.WimoMailSender;
import com.ruoyi.wimo.config.WimoProperties;
import com.ruoyi.wimo.domain.WimoMailLog;
import com.ruoyi.wimo.domain.WimoUser;
import com.ruoyi.wimo.exception.WimoException;
import com.ruoyi.wimo.mapper.WimoMailLogMapper;
import com.ruoyi.wimo.mapper.WimoUserMapper;

/**
 * 会员账号服务：验证码、注册、密码登录、验证码登录、找回密码、登出、资料。
 */
@Service
public class WimoAuthService
{
    private static final Logger log = LoggerFactory.getLogger(WimoAuthService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired
    private WimoUserMapper userMapper;

    @Autowired
    private WimoMailLogMapper mailLogMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private WimoTokenService tokenService;

    @Autowired
    private WimoProperties props;

    @Autowired
    private WimoMailSender mailSender;

    @Autowired
    private WimoDiamondService diamondService;

    /** 发送邮箱验证码（带频控，mock 模式返回演示码） */
    public Map<String, Object> sendCode(String rawEmail, String scene)
    {
        String email = norm(rawEmail);
        if (!EMAIL.matcher(email).matches())
        {
            throw new WimoException("邮箱格式不正确");
        }
        if (!"register".equals(scene) && !"login".equals(scene) && !"reset".equals(scene))
        {
            throw new WimoException("验证码场景不正确");
        }
        String limitKey = "wimo:code:limit:" + scene + ":" + email;
        if (redisCache.getCacheObject(limitKey) != null)
        {
            throw new WimoException("发送过于频繁，请稍后再试");
        }
        String code = (props.getDemoCode() != null && !props.getDemoCode().isEmpty())
                ? props.getDemoCode()
                : String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

        redisCache.setCacheObject(codeKey(email, scene), code, props.getCodeTtl(), TimeUnit.SECONDS);
        redisCache.setCacheObject(limitKey, code, props.getCodeLimitSeconds(), TimeUnit.SECONDS);

        boolean ok = true;
        try
        {
            mailSender.send(email, scene, code);
        }
        catch (Exception e)
        {
            ok = false;
            log.warn("验证码邮件发送失败 email={} scene={} err={}", email, scene, e.getMessage());
        }
        WimoMailLog l = new WimoMailLog();
        l.setEmail(email);
        l.setScene(scene);
        l.setCode(props.getMailProvider().equals("mock") ? code : "***");
        l.setProvider(props.getMailProvider());
        l.setSuccess(ok ? "1" : "0");
        l.setRemark(ok ? "发送成功" : "发送失败，请检查邮件通道配置");
        l.setCreateTime(new Date());
        mailLogMapper.insertLog(l);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sent", ok);
        r.put("provider", props.getMailProvider());
        r.put("expiresIn", props.getCodeTtl());
        if ("mock".equals(props.getMailProvider()))
        {
            r.put("demoCode", code);
        }
        return r;
    }

    /** 注册：邮箱 + 验证码 + 密码 + 确认密码 + 协议 */
    public Map<String, Object> register(String rawEmail, String code, String password,
                                        String confirmPassword, Boolean agreed)
    {
        String email = norm(rawEmail);
        if (!Boolean.TRUE.equals(agreed))
        {
            throw new WimoException("请先同意用户协议与隐私政策");
        }
        if (password == null || password.length() < 6 || password.length() > 32)
        {
            throw new WimoException("密码长度需为 6–32 位");
        }
        if (!password.equals(confirmPassword))
        {
            throw new WimoException("两次输入的密码不一致");
        }
        if (!EMAIL.matcher(email).matches())
        {
            throw new WimoException("邮箱格式不正确");
        }
        verifyCode(email, "register", code);
        if (userMapper.selectByEmail(email) != null)
        {
            throw new WimoException("该邮箱已注册，请直接登录");
        }
        WimoUser u = new WimoUser();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setNickname(email.substring(0, email.indexOf('@')));
        u.setStatus("0");
        u.setAgreed("1");
        u.setDiamondBalance(0L);
        u.setDiamondFrozen(0L);
        u.setDiamondTotal(0L);
        u.setDiamondUsed(0L);
        u.setCreateTime(new Date());
        u.setUpdateTime(u.getCreateTime());
        userMapper.insertUser(u);
        // 注册赠送体验钻石（写 gift 流水）
        diamondService.giftOnRegister(u.getUserId());
        return issue(userMapper.selectById(u.getUserId()));
    }

    /** 密码登录 */
    public Map<String, Object> loginByPassword(String rawEmail, String password)
    {
        String email = norm(rawEmail);
        WimoUser u = userMapper.selectByEmail(email);
        if (u == null)
        {
            throw new WimoException("账号不存在，请先注册");
        }
        if (password == null || !encoder.matches(password, u.getPasswordHash()))
        {
            throw new WimoException("密码不正确");
        }
        checkStatus(u);
        return issue(u);
    }

    /** 验证码登录（账号必须已存在，不自动注册） */
    public Map<String, Object> loginByCode(String rawEmail, String code)
    {
        String email = norm(rawEmail);
        verifyCode(email, "login", code);
        WimoUser u = userMapper.selectByEmail(email);
        if (u == null)
        {
            throw new WimoException("账号不存在，请先注册");
        }
        checkStatus(u);
        return issue(u);
    }

    /** 找回密码 */
    public void resetPassword(String rawEmail, String code, String newPassword)
    {
        String email = norm(rawEmail);
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 32)
        {
            throw new WimoException("新密码长度需为 6–32 位");
        }
        verifyCode(email, "reset", code);
        WimoUser u = userMapper.selectByEmail(email);
        if (u == null)
        {
            throw new WimoException("账号不存在");
        }
        userMapper.updatePassword(u.getUserId(), encoder.encode(newPassword), new Date());
        // 密码变更后使所有已签发令牌失效的简化处理：前端登出即可，此处仅记日志
        log.info("会员重置密码成功 userId={}", u.getUserId());
    }

    public Map<String, Object> profile(String token)
    {
        Long userId = tokenService.resolve(token);
        if (userId == null)
        {
            throw new WimoException("登录状态已失效");
        }
        WimoUser u = userMapper.selectById(userId);
        if (u == null)
        {
            throw new WimoException("账号不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getUserId());
        m.put("email", u.getEmail());
        m.put("nickname", u.getNickname());
        m.put("createTime", u.getCreateTime());
        m.put("diamonds", u.getDiamondBalance() == null ? 0L : u.getDiamondBalance());
        m.put("diamondFrozen", u.getDiamondFrozen() == null ? 0L : u.getDiamondFrozen());
        m.put("diamondTotal", u.getDiamondTotal() == null ? 0L : u.getDiamondTotal());
        m.put("diamondUsed", u.getDiamondUsed() == null ? 0L : u.getDiamondUsed());
        return m;
    }

    public void logout(String token)
    {
        tokenService.revoke(token);
    }

    private void checkStatus(WimoUser u)
    {
        if ("1".equals(u.getStatus()))
        {
            throw new WimoException("账号已被停用");
        }
    }

    private Map<String, Object> issue(WimoUser u)
    {
        userMapper.updateLastLogin(u.getUserId(), new Date());
        String token = tokenService.issue(u.getUserId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        m.put("userId", u.getUserId());
        m.put("email", u.getEmail());
        m.put("nickname", u.getNickname());
        m.put("diamonds", u.getDiamondBalance() == null ? 0L : u.getDiamondBalance());
        m.put("diamondFrozen", u.getDiamondFrozen() == null ? 0L : u.getDiamondFrozen());
        m.put("expiresIn", props.getTokenDays() * 86400);
        return m;
    }

    private void verifyCode(String email, String scene, String code)
    {
        if (code == null || code.trim().isEmpty())
        {
            throw new WimoException("请输入验证码");
        }
        Object cached = redisCache.getCacheObject(codeKey(email, scene));
        if (cached == null)
        {
            throw new WimoException("验证码已过期，请重新获取");
        }
        if (!code.trim().equals(cached.toString()))
        {
            throw new WimoException("验证码不正确");
        }
        redisCache.deleteObject(codeKey(email, scene));
    }

    private String codeKey(String email, String scene)
    {
        return "wimo:code:" + scene + ":" + email;
    }

    private String norm(String email)
    {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
