package org.example.groommvp.domain.product.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @NotBlank(message = "상품명을 입력해주세요.")
    @Size(max = 50, message = "상품명은 최대 50자까지 입력 가능합니다.")
    @Column(name = "product_name", nullable = false, length = 50)
    private String productName;

    @NotNull(message = "상품 가격을 입력해주세요.")
    @Positive(message = "상품 가격은 0보다 커야 합니다.")
    @Column(name = "product_price", nullable = false)
    private Integer productPrice;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public ProductEntity(
            String productName,
            Integer productPrice
    ) {
        this.productName = productName;
        this.productPrice = productPrice;
    }

    public void update(
            String productName,
            Integer productPrice
    ) {
        this.productName = productName;
        this.productPrice = productPrice;
    }
}
