package org.example.groommvp.domain.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductResponse {

    private String productName;
    private Integer productPrice;
}
