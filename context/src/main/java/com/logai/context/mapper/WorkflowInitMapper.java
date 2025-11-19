package com.logai.context.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.context.entity.WorkflowInit;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowInitMapper extends BaseMapper<WorkflowInit> {

    @Select("SELECT * FROM workflow_init WHERE theme_id = #{themeId}")
    List<WorkflowInit> findAllByThemeId(String themeId);
}

