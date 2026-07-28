package org.example.groommvp.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상품 목록 정렬 기준")
public enum ProductSortType {
    LATEST,     //최신순
    POPULAR,    //인기순
    VIEW_COUNT, //조회순
    PRICE_ASC,  //가격 낮은순
    PRICE_DESC; //가격 높은순

    public static ProductSortType from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        try {
            return ProductSortType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LATEST;
        }
    }
}