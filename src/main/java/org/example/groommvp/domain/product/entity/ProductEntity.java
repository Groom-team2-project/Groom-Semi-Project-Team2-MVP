package org.example.groommvp.domain.product.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long product_id;

    @NotBlank(message = "제품명을 입력하세요.")
    @Size(max = 50, message = "제품명은 최대 50자까지 입력 가능합니다.")
    private String product_name;

    @NotNull(message = "제품 가격을 입력하세요.")
    @Positive(message = "제품 가격은 0보다 커야 합니다.")
    private  Integer product_price;

    @CreationTimestamp
    private LocalDateTime product_created;
    @UpdateTimestamp
    private LocalDateTime updated_At;
}
