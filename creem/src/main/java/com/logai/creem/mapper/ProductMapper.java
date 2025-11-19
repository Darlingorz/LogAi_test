package com.logai.creem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.creem.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    @Select("SELECT * FROM products WHERE product_id = #{productId}")
    Product findByProductId(String productId);
}