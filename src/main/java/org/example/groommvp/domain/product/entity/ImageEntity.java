package org.example.groommvp.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.groommvp.global.entity.BaseEntity;

@Entity
@Getter
@Table(name = "product_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "detail_image", nullable = false)
    private String detailImage;

    @Builder
    public ImageEntity(
            ProductEntity product,
            String detailImage
    ) {
        this.product = product;
        this.detailImage = detailImage;
    }

    public void update(
            String detailImage
    ) {
        this.detailImage = detailImage;
    }
}