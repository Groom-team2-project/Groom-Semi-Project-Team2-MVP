package org.example.groommvp.domain.stock.entity;

import java.time.LocalDateTime;

import org.example.groommvp.domain.product.entity.ProductEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private ProductEntity product;

    @Column(name = "stocks", nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Stock(ProductEntity product, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Stock quantity must be greater than or equal to 0.");
        }
        this.product = product;
        this.quantity = quantity;
    }

    public void decrease(int purchaseQuantity) {
        if (purchaseQuantity <= 0) {
            throw new IllegalArgumentException("Purchase quantity must be greater than or equal to 1.");
        }
        if (quantity < purchaseQuantity) {
            throw new IllegalStateException("Not enough stock.");
        }
        quantity -= purchaseQuantity;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
