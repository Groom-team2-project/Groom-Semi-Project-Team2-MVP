package org.example.groommvp.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.groommvp.global.entity.BaseEntity;

@Entity
@Getter
@Table(
        name = "product_images",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_images_product_id",
                columnNames = "product_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Builder
    public ImageEntity(
            ProductEntity product,
            String imageUrl
    ) {
        this.product = product;
        this.imageUrl = imageUrl;
    }
}
