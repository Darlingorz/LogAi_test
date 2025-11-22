package com.logai.creem.controller;

import com.logai.creem.entity.Product;
import com.logai.creem.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/creem/products")
public class ProductController {

    private final ProductService productService;

    /**
     * 查询商品
     *
     * @return 商品列表
     */
    @GetMapping("/list")
    public List<Product> listProducts() {
        return productService.findAll();
    }

    /**
     * 同步商品列表
     *
     * @return 同步结果
     */
    @GetMapping("/sync")
    public List<Product> syncProducts() {
        return productService.syncProducts();
    }
}
