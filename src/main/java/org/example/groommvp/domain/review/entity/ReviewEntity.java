package org.example.groommvp.domain.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.groommvp.global.entity.BaseEntity;
import java.time.LocalDateTime;


@Entity
@Getter
@Table(name="reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "content", length = 100)
    private String content;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public ReviewEntity(Long productId, Long memberId, String content, Integer rating) {
        this.productId = productId;
        this.memberId = memberId;
        this.content = content;
        this.rating = rating;
    }

    public void update(String content, Integer rating) {
        this.content = content;
        this.rating = rating;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

}
