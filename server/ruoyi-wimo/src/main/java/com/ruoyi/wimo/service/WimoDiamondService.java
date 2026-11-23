package com.ruoyi.wimo.service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.wimo.config.WimoProperties;
import com.ruoyi.wimo.domain.WimoDiamondLog;
import com.ruoyi.wimo.domain.WimoTask;
import com.ruoyi.wimo.domain.WimoUser;
import com.ruoyi.wimo.exception.WimoException;
import com.ruoyi.wimo.mapper.WimoDiamondLogMapper;
import com.ruoyi.wimo.mapper.WimoTaskMapper;
import com.ruoyi.wimo.mapper.WimoUserMapper;

/**
 * 钻石账户服务：两阶段结算（预扣 → 成功扣减 / 失败退还）
 *
 * <p>所有余额变动均为带条件的原子 UPDATE，防止并发超扣；每次变动写一条流水。
 * 匿名演示用户（userId=0）默认不计费。</p>
 *
 * @author wimo
 */
@Service
public class WimoDiamondService
{
    @Autowired private WimoUserMapper userMapper;
    @Autowired private WimoDiamondLogMapper logMapper;
    @Autowired private WimoTaskMapper taskMapper;
    @Autowired private WimoProperties props;

    /** 当前钱包快照（未登录返回全 0） */
    public Map<String, Object> balanceOf(Long userId)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        if (userId == null || userId <= 0)
        {
            m.put("balance", 0L); m.put("frozen", 0L); m.put("total", 0L); m.put("used", 0L);
            m.put("anonymous", true);
            return m;
        }
        WimoUser u = userMapper.selectById(userId);
        long b = u == null ? 0 : nz(u.getDiamondBalance());
        long f = u == null ? 0 : nz(u.getDiamondFrozen());
        long t = u == null ? 0 : nz(u.getDiamondTotal());
        long used = u == null ? 0 : nz(u.getDiamondUsed());
        m.put("balance", b); m.put("frozen", f); m.put("total", t); m.put("used", used);
        m.put("anonymous", false);
        return m;
    }

    /** 注册赠送，写 gift 流水 */
    public void giftOnRegister(Long userId)
    {
        long gift = props.getRegisterGift();
        if (gift <= 0) return;
        WimoUser before = userMapper.selectById(userId);
        userMapper.adjustDiamonds(userId, gift);
        WimoUser after = userMapper.selectById(userId);
        writeLog(userId, "gift", gift, before, after, null, "", "system", "注册赠送体验钻石");
    }

    /**
     * 预扣（生成任务提交时）：可用余额 → 冻结。
     * 余额不足抛 WimoException，由 Controller 转成友好提示。
     */
    @Transactional(rollbackFor = Exception.class)
    public synchronized void freeze(Long userId, long cost, Long taskId, String workflowKey)
    {
        if (userId == null || userId <= 0 || cost <= 0) return;
        WimoUser before = userMapper.selectById(userId);
        int n = userMapper.freezeDiamonds(userId, cost);
        if (n == 0)
        {
            throw new WimoException("钻石余额不足，本次需 " + cost + " 钻石，当前 "
                    + (before == null ? 0 : nz(before.getDiamondBalance())) + " 钻石");
        }
        WimoUser after = userMapper.selectById(userId);
        writeLog(userId, "freeze", -cost, before, after, taskId, workflowKey, "system", "生成任务预扣");
    }

    /** 成功结算：冻结 → 已消耗（幂等，仅处理 diamondState=0 的任务） */
    @Transactional(rollbackFor = Exception.class)
    public synchronized void settle(WimoTask task)
    {
        if (!needSettle(task)) return;
        long cost = nz(task.getCostDiamonds());
        Long uid = task.getUserId();
        WimoUser before = userMapper.selectById(uid);
        userMapper.settleFrozen(uid, cost);
        WimoUser after = userMapper.selectById(uid);
        writeLog(uid, "consume", -cost, before, after, task.getTaskId(), task.getTaskType(), "system", "生成完成扣减");
        markState(task.getTaskId(), "1");
    }

    /** 失败 / 取消退还：冻结 → 可用余额（幂等） */
    @Transactional(rollbackFor = Exception.class)
    public synchronized void refund(WimoTask task)
    {
        if (!needSettle(task)) return;
        long cost = nz(task.getCostDiamonds());
        Long uid = task.getUserId();
        WimoUser before = userMapper.selectById(uid);
        userMapper.refundFrozen(uid, cost);
        WimoUser after = userMapper.selectById(uid);
        writeLog(uid, "refund", cost, before, after, task.getTaskId(), task.getTaskType(), "system", "失败/取消退还");
        markState(task.getTaskId(), "2");
    }

    /** 管理端人工调整 / 充值（delta 可正可负） */
    @Transactional(rollbackFor = Exception.class)
    public synchronized Map<String, Object> adjust(Long userId, long delta, String operator, String remark)
    {
        if (userId == null || userId <= 0) throw new WimoException("会员不存在");
        if (userMapper.selectById(userId) == null) throw new WimoException("会员不存在");
        WimoUser before = userMapper.selectById(userId);
        int n = userMapper.adjustDiamonds(userId, delta);
        if (n == 0) throw new WimoException("调整后余额不能为负");
        WimoUser after = userMapper.selectById(userId);
        writeLog(userId, "adjust", delta, before, after, null, "", operator == null ? "admin" : operator,
                remark == null || remark.isEmpty() ? "管理端调整" : remark);
        return balanceOf(userId);
    }

    /** 用户端流水（带上限） */
    public List<WimoDiamondLog> myLogs(Long userId, int limit)
    {
        return userId == null || userId <= 0 ? List.of() : logMapper.selectByUser(userId, Math.min(100, Math.max(1, limit)));
    }

    /** 管理端分页流水 */
    public Map<String, Object> pageLogs(Long userId, String changeType, int page, int size)
    {
        int p = Math.max(1, page), s = Math.min(100, Math.max(1, size));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", logMapper.countPage(userId, changeType));
        m.put("page", p);
        m.put("size", s);
        m.put("list", logMapper.selectPage(userId, changeType, (p - 1) * s, s));
        return m;
    }

    /** 看板：钻石总览 */
    public Map<String, Object> overview()
    {
        Map<String, Object> m = new LinkedHashMap<>(userMapper.diamondOverview());
        m.put("logCount", logMapper.countAll());
        m.put("byType", logMapper.groupByType());
        m.put("consumeTrend", logMapper.consumeTrend(7));
        return m;
    }

    /** 该任务是否需要结算（匿名 / 未冻结 / 已结算过 均跳过） */
    private boolean needSettle(WimoTask t)
    {
        if (t == null || t.getTaskId() == null) return false;
        WimoTask cur = taskMapper.selectTaskById(t.getTaskId());
        if (cur == null || !"0".equals(cur.getDiamondState())) return false;
        if (cur.getUserId() == null || cur.getUserId() <= 0 || nz(cur.getCostDiamonds()) <= 0) return false;
        return true;
    }

    private void markState(Long taskId, String state)
    {
        WimoTask u = new WimoTask();
        u.setTaskId(taskId);
        u.setDiamondState(state);
        u.setUpdateTime(new Date());
        taskMapper.updateTask(u);
    }

    private void writeLog(Long userId, String type, long amount, WimoUser before, WimoUser after,
                          Long taskId, String workflowKey, String operator, String remark)
    {
        WimoDiamondLog l = new WimoDiamondLog();
        l.setUserId(userId);
        l.setChangeType(type);
        l.setAmount(amount);
        l.setBalanceBefore(before == null ? 0 : nz(before.getDiamondBalance()));
        l.setBalanceAfter(after == null ? 0 : nz(after.getDiamondBalance()));
        l.setFrozenBefore(before == null ? 0 : nz(before.getDiamondFrozen()));
        l.setFrozenAfter(after == null ? 0 : nz(after.getDiamondFrozen()));
        l.setTaskId(taskId);
        l.setWorkflowKey(workflowKey == null ? "" : workflowKey);
        l.setOperator(operator);
        l.setRemark(remark);
        l.setCreateTime(new Date());
        logMapper.insertLog(l);
    }

    private long nz(Long v) { return v == null ? 0L : v; }
}
