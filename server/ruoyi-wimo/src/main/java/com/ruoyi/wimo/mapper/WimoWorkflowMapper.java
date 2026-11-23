package com.ruoyi.wimo.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.ruoyi.wimo.domain.WimoWorkflow;

/** 工作流 / 智能体 Mapper */
@Mapper
public interface WimoWorkflowMapper
{
    String COLS = "workflow_id as workflowId, workflow_key as workflowKey, name_zh as nameZh, name_en as nameEn, "
            + "group_key as groupKey, capability, allowed_models as allowedModels, default_model as defaultModel, "
            + "base_cost as baseCost, unit, unit_cost as unitCost, unit_field as unitField, multipliers, "
            + "min_cost as minCost, max_cost as maxCost, steps_json as stepsJson, enabled, sort_no as sortNo, "
            + "remark, create_time as createTime, update_time as updateTime";

    @Select("select " + COLS + " from wimo_workflow order by sort_no, workflow_id")
    List<WimoWorkflow> selectAll();

    @Select("select " + COLS + " from wimo_workflow where workflow_key = #{workflowKey}")
    WimoWorkflow selectByKey(@Param("workflowKey") String workflowKey);

    @Select("select " + COLS + " from wimo_workflow where workflow_id = #{workflowId}")
    WimoWorkflow selectById(@Param("workflowId") Long workflowId);

    @Select("select count(*) from wimo_workflow")
    int countAll();

    @Insert("insert into wimo_workflow (workflow_key, name_zh, name_en, group_key, capability, allowed_models, "
            + "default_model, base_cost, unit, unit_cost, unit_field, multipliers, min_cost, max_cost, steps_json, "
            + "enabled, sort_no, remark, create_time, update_time) values "
            + "(#{workflowKey}, #{nameZh}, #{nameEn}, #{groupKey}, #{capability}, #{allowedModels}, "
            + "#{defaultModel}, #{baseCost}, #{unit}, #{unitCost}, #{unitField}, #{multipliers}, #{minCost}, #{maxCost}, #{stepsJson}, "
            + "#{enabled}, #{sortNo}, #{remark}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "workflowId")
    int insertWorkflow(WimoWorkflow w);

    @Update("update wimo_workflow set name_zh = #{nameZh}, name_en = #{nameEn}, group_key = #{groupKey}, "
            + "capability = #{capability}, allowed_models = #{allowedModels}, default_model = #{defaultModel}, "
            + "base_cost = #{baseCost}, unit = #{unit}, unit_cost = #{unitCost}, unit_field = #{unitField}, "
            + "multipliers = #{multipliers}, min_cost = #{minCost}, max_cost = #{maxCost}, steps_json = #{stepsJson}, "
            + "enabled = #{enabled}, sort_no = #{sortNo}, remark = #{remark}, update_time = #{updateTime} "
            + "where workflow_id = #{workflowId}")
    int updateWorkflow(WimoWorkflow w);
}
