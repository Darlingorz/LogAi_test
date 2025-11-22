package com.logai.creem.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.logai.creem.entity.Product;
import com.logai.creem.mapper.ProductMapper;
import com.logai.creem.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productRepository;

    @Value("${creem.api-key}")
    private String apiKey;

    @Value("${creem.base-url}")
    private String baseUrl;

    @Value("${creem.search-products-path}")
    private String searchProductsPath;

    @Override
    public List<Product> syncProducts() {
        log.info("开始同步产品...");
        try {
            JSONObject responseBody = fetchProductsFromCreem();

            List<Product> products = mapProducts(responseBody);

            if (CollectionUtils.isEmpty(products)) {
                log.warn("Creem API 未返回产品数据，本地不会更新");
                return Collections.emptyList();
            }

            List<Product> persisted = new ArrayList<>();

            for (Product p : products) {
                Product saved = upsertProduct(p);
                persisted.add(saved);
            }

            log.info("成功同步 {} 个产品", persisted.size());
            return persisted;

        } catch (Exception ex) {
            log.error("同步 Creem 产品失败: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to synchronize Creem products", ex);
        }
    }

    @Override
    public List<Product> findAll() {
        return productRepository.selectList(null);
    }

    private JSONObject fetchProductsFromCreem() {
        try (HttpResponse response = HttpRequest.get(baseUrl + searchProductsPath)
                .header("x-api-key", apiKey)
                .execute()) {
            if (response.isOk()) {
                return new JSONObject(response.body());
            } else {
                log.error("调用 Creem 产品搜索接口失败，状态码：{}，响应：{}", response.getStatus(), response.body());
                throw new RuntimeException("Creem product API request failed: " + response.getStatus());
            }
        } catch (Exception ex) {
            log.error("调用 Creem 产品搜索接口失败: {}", ex.getMessage(), ex);
            throw new RuntimeException(ex.getMessage(), ex);
        }

    }

    private Product upsertProduct(Product product) {
        Product existing = productRepository.findByProductId(product.getProductId());
        if (existing != null) {
            existing.setName(product.getName());
            existing.setDescription(product.getDescription());
            existing.setPrice(product.getPrice());
            existing.setCurrency(product.getCurrency());
            existing.setBillingType(product.getBillingType());
            existing.setBillingPeriod(product.getBillingPeriod());
            existing.setMode(product.getMode());
            existing.setStatus(product.getStatus());
            existing.setTaxMode(product.getTaxMode());
            existing.setTaxCategory(product.getTaxCategory());
            existing.setProductUrl(product.getProductUrl());
            existing.setDefaultSuccessUrl(product.getDefaultSuccessUrl());
            existing.setImageUrl(product.getImageUrl());
            existing.setUpdatedAt(product.getUpdatedAt());
            productRepository.updateById(existing);
        } else {
            productRepository.insert(product);
        }
        Product byProductId = productRepository.findByProductId(product.getProductId());
        log.debug("已保存或更新产品: {}", byProductId.getId());
        return byProductId;
    }

    private List<Product> mapProducts(JSONObject responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return Collections.emptyList();
        }

        JSONArray items = responseBody.optJSONArray("items");
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<Product> products = new ArrayList<>();

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            Product product = new Product();
            product.setProductId(item.optString("id"));
            product.setName(item.optString("name"));
            product.setDescription(item.optString("description"));
            product.setPrice(item.optDouble("price"));
            product.setCurrency(item.optString("currency"));
            product.setBillingType(item.optString("billing_type"));
            product.setBillingPeriod(item.optString("billing_period"));
            product.setStatus(item.optString("status"));
            product.setTaxMode(item.optString("tax_mode"));
            product.setTaxCategory(item.optString("tax_category"));
            product.setProductUrl(item.optString("product_url"));
            product.setDefaultSuccessUrl(item.optString("default_success_url"));
            product.setMode(item.optString("mode"));
            product.setImageUrl(item.optString("image_url"));
            product.setCreatedAt(OffsetDateTime.parse(item.optString("created_at")));
            product.setUpdatedAt(OffsetDateTime.parse(item.optString("updated_at")));
            products.add(product);
        }
        return products;
    }


}
