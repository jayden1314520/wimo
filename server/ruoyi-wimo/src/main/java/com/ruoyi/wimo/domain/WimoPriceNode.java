package com.ruoyi.wimo.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 价格节点 wimo_price_node —— 计费规则树上的维度档位
 *
 * <p>parent_id 自关联成树：能力(虚拟根) → 维度分组(group) → 具体档位(option)。
 * 每个 option 节点带 factor 钻石倍率；管理端逐节点调价即时生效，
 * 改一个「4K」档就等于改了该能力下所有工作流的 4K 倍率。</p>
 *
 * <p>数值型维度(如字数 wordCount)用 minValue/maxValue 区间匹配，其余用 optionValue 精确匹配。</p>
 *
 * @author wimo
 */
@Data
public class WimoPriceNode
{
    private Long nodeId;

    /** 父节点ID：0=能力根下的维度分组；其余=分组节点ID */
    private Long parentId;

    private String capabilityKey;

    /** group 维度分组 / option 具体档位 / model 模型倍率 */
    private String nodeType;

    /** 维度 key：resolution/videoResolution/quality/duration/wordCount/language/model */
    private String dimension;

    /** 节点唯一 key（能力:维度:档位） */
    private String nodeKey;

    /** 档位值，与提交参数匹配 */
    private String optionValue;

    private String labelZh;
    private String labelEn;

    /** 钻石倍率 */
    private BigDecimal factor;

    /** 档位级单价覆盖（可选，优先于能力/工作流单价） */
    private BigDecimal unitCost;

    /** 数值维度区间下界（含） */
    private Long minValue;

    /** 数值维度区间上界（含） */
    private Long maxValue;

    private String enabled;
    private Integer sortNo;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
