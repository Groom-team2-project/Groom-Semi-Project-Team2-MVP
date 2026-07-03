package org.example.groommvp.domain.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PurchaseRequest(
        @NotNull(message = "결제 수량은 필수입니다.")
        @Min(value = 1, message = "결제 수량은 적어도 1개 이상이어야 합니다.")
        Integer quantity
) {
}
