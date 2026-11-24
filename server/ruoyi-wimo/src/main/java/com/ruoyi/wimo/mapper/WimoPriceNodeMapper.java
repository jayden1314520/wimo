package com.ruoyi.wimo.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.ruoyi.wimo.domain.WimoPriceNode;

/** 价格节点 Mapper（计费规则树档位） */
@Mapper
public interface WimoPriceNodeMapper
{
    String COLS = "node_id as nodeId, parent_id as parentId, capability_key as capabilityKey, "
            + "node_type as nodeType, dimension, node_key as nodeKey, option_value as optionValue, "
            + "label_zh as labelZh, label_en as labelEn, factor, unit_cost as unitCost, "
            + "min_value as `minValue`, max_value as `maxValue`, enabled, sort_no as sortNo, "
            + "remark, create_time as createTime, update_time as updateTime";

    @Select("select " + COLS + " from wimo_price_node order by capability_key, dimension, sort_no, node_id")
    List<WimoPriceNode> selectAll();

    @Select("select " + COLS + " from wimo_price_node where capability_key = #{capabilityKey} "
            + "order by dimension, sort_no, node_id")
    List<WimoPriceNode> selectByCapability(@Param("capabilityKey") String capabilityKey);

    @Select("select " + COLS + " from wimo_price_node where capability_key = #{capabilityKey} and enabled = '1' "
            + "order by dimension, sort_no, node_id")
    List<WimoPriceNode> selectEnabledByCapability(@Param("capabilityKey") String capabilityKey);

    @Select("select " + COLS + " from wimo_price_node where capability_key = #{capabilityKey} "
            + "and dimension = #{dimension} and enabled = '1' order by sort_no, node_id")
    List<WimoPriceNode> selectEnabledByDimension(@Param("capabilityKey") String capabilityKey,
                                                 @Param("dimension") String dimension);

    @Select("select " + COLS + " from wimo_price_node where node_key = #{nodeKey}")
    WimoPriceNode selectByKey(@Param("nodeKey") String nodeKey);

    @Select("select " + COLS + " from wimo_price_node where node_id = #{nodeId}")
    WimoPriceNode selectById(@Param("nodeId") Long nodeId);

    @Select("select count(*) from wimo_price_node")
    int countAll();

    @Select("select count(*) from wimo_price_node where capability_key = #{capabilityKey}")
    int countByCapability(@Param("capabilityKey") String capabilityKey);

    @Insert("insert into wimo_price_node (parent_id, capability_key, node_type, dimension, node_key, option_value, "
            + "label_zh, label_en, factor, unit_cost, min_value, max_value, enabled, sort_no, remark, create_time, update_time) values "
            + "(#{parentId}, #{capabilityKey}, #{nodeType}, #{dimension}, #{nodeKey}, #{optionValue}, "
            + "#{labelZh}, #{labelEn}, #{factor}, #{unitCost}, #{minValue}, #{maxValue}, #{enabled}, #{sortNo}, #{remark}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "nodeId")
    int insertNode(WimoPriceNode n);

    @Update("update wimo_price_node set parent_id = #{parentId}, capability_key = #{capabilityKey}, "
            + "node_type = #{nodeType}, dimension = #{dimension}, option_value = #{optionValue}, "
            + "label_zh = #{labelZh}, label_en = #{labelEn}, factor = #{factor}, unit_cost = #{unitCost}, "
            + "min_value = #{minValue}, max_value = #{maxValue}, enabled = #{enabled}, sort_no = #{sortNo}, "
            + "remark = #{remark}, update_time = #{updateTime} where node_id = #{nodeId}")
    int updateNode(WimoPriceNode n);

    @Delete("delete from wimo_price_node where node_id = #{nodeId}")
    int deleteById(@Param("nodeId") Long nodeId);
}
