package com.ruoyi.wimo.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.ruoyi.wimo.domain.WimoCapability;

/** 能力层 Mapper（计费规则树根） */
@Mapper
public interface WimoCapabilityMapper
{
    String COLS = "capability_id as capabilityId, capability_key as capabilityKey, name_zh as nameZh, "
            + "name_en as nameEn, icon, base_cost as baseCost, unit, unit_cost as unitCost, unit_field as unitField, "
            + "min_cost as minCost, max_cost as maxCost, default_model as defaultModel, enabled, sort_no as sortNo, "
            + "remark, create_time as createTime, update_time as updateTime";

    @Select("select " + COLS + " from wimo_capability order by sort_no, capability_id")
    List<WimoCapability> selectAll();

    @Select("select " + COLS + " from wimo_capability where capability_key = #{capabilityKey}")
    WimoCapability selectByKey(@Param("capabilityKey") String capabilityKey);

    @Select("select " + COLS + " from wimo_capability where capability_id = #{capabilityId}")
    WimoCapability selectById(@Param("capabilityId") Long capabilityId);

    @Select("select count(*) from wimo_capability")
    int countAll();

    @Insert("insert into wimo_capability (capability_key, name_zh, name_en, icon, base_cost, unit, unit_cost, "
            + "unit_field, min_cost, max_cost, default_model, enabled, sort_no, remark, create_time, update_time) values "
            + "(#{capabilityKey}, #{nameZh}, #{nameEn}, #{icon}, #{baseCost}, #{unit}, #{unitCost}, "
            + "#{unitField}, #{minCost}, #{maxCost}, #{defaultModel}, #{enabled}, #{sortNo}, #{remark}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "capabilityId")
    int insertCapability(WimoCapability c);

    @Update("update wimo_capability set name_zh = #{nameZh}, name_en = #{nameEn}, icon = #{icon}, "
            + "base_cost = #{baseCost}, unit = #{unit}, unit_cost = #{unitCost}, unit_field = #{unitField}, "
            + "min_cost = #{minCost}, max_cost = #{maxCost}, default_model = #{defaultModel}, "
            + "enabled = #{enabled}, sort_no = #{sortNo}, remark = #{remark}, update_time = #{updateTime} "
            + "where capability_id = #{capabilityId}")
    int updateCapability(WimoCapability c);

    @Delete("delete from wimo_capability where capability_id = #{capabilityId}")
    int deleteById(@Param("capabilityId") Long capabilityId);
}
