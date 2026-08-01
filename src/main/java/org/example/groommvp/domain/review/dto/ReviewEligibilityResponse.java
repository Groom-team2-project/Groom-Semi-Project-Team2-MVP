package org.example.groommvp.domain.review.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 로그인한 회원이 이 상품에 리뷰를 "새로" 작성할 수 있는 상태인지 여부.
 * 프론트가 리뷰 작성 영역을 보여줄지 말지 판단하는 데 쓴다.
 *
 * <p>true가 되려면: 로그인 상태 + 결제완료(COMPLETED) 구매 이력 있음 + 아직 살아있는 리뷰가 없음, 세 조건을 다 만족해야 한다.
 * 로그인 안 했거나, 구매를 안 했거나, 이미 리뷰를 써놓은 상태면 전부 false.
 */
@Getter
@Builder
public class ReviewEligibilityResponse {
    private boolean eligible;
}
