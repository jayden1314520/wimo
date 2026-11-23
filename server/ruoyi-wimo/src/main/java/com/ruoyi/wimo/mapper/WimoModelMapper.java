package com.ruoyi.wimo.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.ruoyi.wimo.domain.WimoModel;

/** 模型注册表 Mapper */
@Mapper
public interface WimoModelMapper
{
    String COLS = "model_id as modelId, model_key as modelKey, name_zh as nameZh, name_en as nameEn, "
            + "capability, provider, endpoint, credential_ref as credentialRef, "
            + "cost_multiplier as costMultiplier, config_json as configJson, enabled, sort_no as sortNo, "
            + "remark, create_time as createTime, update_time as updateTime";

    @Select("select " + COLS + " from wimo_model order by sort_no, model_id")
    List<WimoModel> selectAll();

    @Select("select " + COLS + " from wimo_model where model_key = #{modelKey}")
    WimoModel selectByKey(@Param("modelKey") String modelKey);

    @Select("select " + COLS + " from wimo_model where model_id = #{modelId}")
    WimoModel selectById(@Param("modelId") Long modelId);

    @Select("select " + COLS + " from wimo_model where capability = #{capability} and enabled = '1' order by sort_no, model_id")
    List<WimoModel> selectEnabledByCapability(@Param("capability") String capability);

    @Select("select count(*) from wimo_model")
    int countAll();

    @Insert("insert into wimo_model (model_key, name_zh, name_en, capability, provider, endpoint, "
            + "credential_ref, cost_multiplier, config_json, enabled, sort_no, remark, create_time, update_time) values "
            + "(#{modelKey}, #{nameZh}, #{nameEn}, #{capability}, #{provider}, #{endpoint}, "
            + "#{credentialRef}, #{costMultiplier}, #{configJson}, #{enabled}, #{sortNo}, #{remark}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "modelId")
    int insertModel(WimoModel model);

    @Update("update wimo_model set name_zh = #{nameZh}, name_en = #{nameEn}, capability = #{capability}, "
            + "provider = #{provider}, endpoint = #{endpoint}, credential_ref = #{credentialRef}, "
            + "cost_multiplier = #{costMultiplier}, config_json = #{configJson}, enabled = #{enabled}, "
            + "sort_no = #{sortNo}, remark = #{remark}, update_time = #{updateTime} where model_id = #{modelId}")
    int updateModel(WimoModel model);

    @Delete("delete from wimo_model where model_id = #{modelId}")
    int deleteById(@Param("modelId") Long modelId);
}
