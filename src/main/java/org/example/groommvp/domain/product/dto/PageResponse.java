package org.example.groommvp.domain.product.dto;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;
import java.util.List;

@Schema(description = "페이지네이션 응답")
@Getter
@Builder

@JsonPropertyOrder({"content", "page", "size", "totalElements"})
public class PageResponse<T>{
    @Schema(description = "현재 페이지의 데이터 목록")
    private List<T> content;
    @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
    private int page;
    @Schema(description = "페이지 크기", example = "10")
    private int size;
    @Schema(description = "전체 데이터 수", example = "100")
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
