package com.ruoyi.wimo.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.wimo.domain.WimoPriceNode;
import com.ruoyi.wimo.exception.WimoException;
import com.ruoyi.wimo.mapper.WimoPriceNodeMapper;

/**
 * 价格节点服务：计费规则树上维度档位的增删改、启停、倍率调整，改动即时生效。
 *
 * @author wimo
 */
@Service
public class WimoPriceNodeService
{
    @Autowired private WimoPriceNodeMapper nodeMapper;

    public List<WimoPriceNode> listAll() { return nodeMapper.selectAll(); }

    public List<WimoPriceNode> nodesOf(String capabilityKey) { return nodeMapper.selectByCapability(capabilityKey); }

    public WimoPriceNode getById(Long id) { return nodeMapper.selectById(id); }

    /** 新增或更新节点。新增时自动生成 nodeKey（能力:维度:档位），并保证唯一 */
    public int save(WimoPriceNode n)
    {
        if (n == null) throw new WimoException("节点不能为空");
        if (isBlank(n.getCapabilityKey())) throw new WimoException("所属能力不能为空");
        if (isBlank(n.getDimension())) throw new WimoException("维度不能为空");
        if (n.getFactor() == null) n.setFactor(BigDecimal.ONE);
        if (n.getFactor().compareTo(BigDecimal.ZERO) <= 0) throw new WimoException("倍率必须大于 0");
        if (n.getEnabled() == null) n.setEnabled("1");
        if (isBlank(n.getNodeType())) n.setNodeType("option");
        if (n.getParentId() == null) n.setParentId(0L);
        n.setUpdateTime(new Date());

        if (n.getNodeId() == null)
        {
            if (isBlank(n.getOptionValue())) throw new WimoException("档位值不能为空");
            String key = n.getCapabilityKey() + ":" + n.getDimension() + ":" + n.getOptionValue();
            if (nodeMapper.selectByKey(key) != null) throw new WimoException("该档位已存在：" + key);
            n.setNodeKey(key);
            // 若未指定父节点，尽量挂到对应维度分组下
            if (n.getParentId() == 0L)
            {
                WimoPriceNode group = nodeMapper.selectByKey(n.getCapabilityKey() + ":" + n.getDimension() + ":group");
                if (group != null) n.setParentId(group.getNodeId());
            }
            n.setCreateTime(n.getUpdateTime());
            return nodeMapper.insertNode(n);
        }
        WimoPriceNode cur = nodeMapper.selectById(n.getNodeId());
        if (cur == null) throw new WimoException("节点不存在");
        // nodeKey / capabilityKey / nodeType 不可改，避免与既有账单快照失配
        n.setNodeKey(cur.getNodeKey());
        n.setCapabilityKey(cur.getCapabilityKey());
        n.setNodeType(cur.getNodeType());
        n.setCreateTime(cur.getCreateTime());
        if (n.getParentId() == null) n.setParentId(cur.getParentId());
        return nodeMapper.updateNode(n);
    }

    /** 启停（不改其它字段） */
    public int toggle(Long id, String enabled)
    {
        WimoPriceNode cur = nodeMapper.selectById(id);
        if (cur == null) throw new WimoException("节点不存在");
        cur.setEnabled("1".equals(enabled) ? "1" : "0");
        cur.setUpdateTime(new Date());
        return nodeMapper.updateNode(cur);
    }

    public int remove(Long id)
    {
        WimoPriceNode cur = nodeMapper.selectById(id);
        if (cur == null) throw new WimoException("节点不存在");
        if ("group".equals(cur.getNodeType()))
        {
            // 删分组前禁止遗留孤儿档位：一并删除该维度下子节点
            for (WimoPriceNode child : nodeMapper.selectByCapability(cur.getCapabilityKey()))
            {
                if (cur.getNodeId().equals(child.getParentId())) nodeMapper.deleteById(child.getNodeId());
            }
        }
        return nodeMapper.deleteById(id);
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
