package com.logai.creem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.creem.entity.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
