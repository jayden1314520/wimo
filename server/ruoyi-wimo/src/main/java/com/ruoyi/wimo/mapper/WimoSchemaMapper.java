package com.ruoyi.wimo.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.ruoyi.wimo.domain.WimoSchema;

/** 功能模板配置 Mapper */
@Mapper
public interface WimoSchemaMapper
{
    @Insert("insert into wimo_schema (task_key, name_zh, name_en, group_key, icon, sort_no, enabled, schema_json, remark, create_time, update_time) "
            + "values (#{taskKey}, #{nameZh}, #{nameEn}, #{groupKey}, #{icon}, #{sortNo}, #{enabled}, #{schemaJson}, #{remark}, #{createTime}, #{updateTime})")
    int insertSchema(WimoSchema schema);

    @Select("select id, task_key as taskKey, name_zh as nameZh, name_en as nameEn, group_key as groupKey, icon, sort_no as sortNo, enabled, schema_json as schemaJson, remark, create_time as createTime, update_time as updateTime "
            + "from wimo_schema where task_key = #{taskKey}")
    WimoSchema selectByKey(@Param("taskKey") String taskKey);

    @Select("select id, task_key as taskKey, name_zh as nameZh, name_en as nameEn, group_key as groupKey, icon, sort_no as sortNo, enabled, schema_json as schemaJson, remark, create_time as createTime, update_time as updateTime "
            + "from wimo_schema order by sort_no, id")
    List<WimoSchema> selectAll();

    @Select("select id, task_key as taskKey, name_zh as nameZh, name_en as nameEn, group_key as groupKey, icon, sort_no as sortNo, enabled, schema_json as schemaJson, remark, create_time as createTime, update_time as updateTime "
            + "from wimo_schema where enabled = '1' order by sort_no, id")
    List<WimoSchema> selectEnabled();

    @Update("update wimo_schema set name_zh = #{nameZh}, name_en = #{nameEn}, group_key = #{groupKey}, "
            + "icon = #{icon}, sort_no = #{sortNo}, enabled = #{enabled}, schema_json = #{schemaJson}, "
            + "remark = #{remark}, update_time = #{updateTime} where id = #{id}")
    int updateSchema(WimoSchema schema);

    @Select("select count(*) from wimo_schema")
    int countAll();
}
