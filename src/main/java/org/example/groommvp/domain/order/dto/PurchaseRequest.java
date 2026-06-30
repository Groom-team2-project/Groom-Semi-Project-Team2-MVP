package org.example.groommvp.domain.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PurchaseRequest(
        @NotNull(message = "Purchase quantity is required.")
        @Min(value = 1, message = "Purchase quantity must be greater than or equal to 1.")
        Integer quantity
) {
}
