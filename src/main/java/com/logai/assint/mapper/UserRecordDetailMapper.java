package com.logai.assint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.assint.entity.UserRecordDetail;
import com.logai.assint.mapper.custom.UserRecordDetailMapperCustom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRecordDetailMapper extends BaseMapper<UserRecordDetail>, UserRecordDetailMapperCustom {

    @Select("SELECT * FROM user_record_detail WHERE record_id = #{recordId}")
    List<UserRecordDetail> findByRecordId(@Param("recordId") Long recordId);

}