package com.logai.creem.service;

import com.logai.creem.entity.Product;

import java.util.List;

public interface ProductService {

    /**
     * 从 Creem API 同步全部产品并写入本地数据库。
     *
     * @return 同步后的产品列表
     */
    List<Product> syncProducts();

    /**
     * 查询本地所有产品。
     *
     * @return 产品流
     */
    List<Product> findAll();
}
