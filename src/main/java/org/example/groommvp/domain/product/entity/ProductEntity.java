package org.example.groommvp.domain.product.entity;

import java.time.LocalDateTime;

import org.example.groommvp.global.entity.BaseEntity;
import org.example.groommvp.domain.category.entity.CategoryEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 50)
    private String productName;

    @Column(name = "product_price", nullable = false)
    private Integer productPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryEntity category;

    /** 삭제된 시간 */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public ProductEntity(
            String productName,
            Integer productPrice,
            CategoryEntity category
    ) {
        this.productName = productName;
        this.productPrice = productPrice;
        this.category = category;
    }

    public void update(
            String productName,
            Integer productPrice
    ) {
        this.productName = productName;
        this.productPrice = productPrice;

    }

    /** 삭제 처리 */
    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }
}
