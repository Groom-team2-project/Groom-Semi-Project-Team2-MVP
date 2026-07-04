package org.example.groommvp.domain.cancel.controller;

import org.example.groommvp.domain.cancel.dto.OrderCancelResponse;
import org.example.groommvp.domain.cancel.service.OrderCancelService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderCancelController {
    
    private final OrderCancelService orderCancelService;

    public OrderCancelController(OrderCancelService orderCancelService) {
        this.orderCancelService = orderCancelService;
    }

	@PostMapping("/{orderId}/cancel")
	public ResponseEntity<CommonResponse<OrderCancelResponse>> cancel(
		@PathVariable Long orderId
	) {
		OrderCancelResponse response = orderCancelService.cancel(orderId);
		return ResponseEntity.ok(
			CommonResponse.success(response, "주문이 취소되었습니다.")
		);
	}
}
