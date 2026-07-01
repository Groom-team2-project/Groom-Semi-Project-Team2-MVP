package org.example.groommvp.domain.product.dto;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;
import java.util.List;

@Getter
@Builder

@JsonPropertyOrder({"content", "page", "size", "totalElements"})
public class PageResponse<T>{
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;


    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())      // 현재 페이지 번호
                .size(page.getSize())        // 페이지 크기
                .totalElements(page.getTotalElements()) // 전체 데이터 개수
                .build();
    }
}
