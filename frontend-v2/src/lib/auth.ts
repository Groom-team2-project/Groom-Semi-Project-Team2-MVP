// JWT 토큰 보관소 — localStorage 기반 (백엔드가 Bearer 토큰 방식)

import { queryClient } from './queryClient';

const ACCESS_KEY = 'soldout_access_token';
const REFRESH_KEY = 'soldout_refresh_token';

/**
 * 액세스 토큰에서 회원 ID를 꺼낸다. (백엔드가 sub 클레임에 memberId를 담는다)
 *
 * <p>서명을 검증하지 않는다. 여기서 알아내려는 건 "지금 화면에 남아 있는 데이터가 누구
 * 것인지" 뿐이고, 실제 권한 판단은 서버가 한다.
 */
function memberIdOf(token: string | null): number | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64)) as { sub?: string };
    const memberId = Number(payload.sub);
    return Number.isFinite(memberId) ? memberId : null;
  } catch {
    return null;
  }
}

/**
 * 저장된 토큰과 새 토큰이 확실히 같은 회원인지.
 *
 * <p>한쪽이라도 해독되지 않으면 <b>다른 회원으로 간주한다.</b> 잘못 판단했을 때의 대가가
 * 비대칭이기 때문이다 — 같은 회원인데 다르다고 보면 재조회 몇 번으로 끝나지만, 다른 회원인데
 * 같다고 보면 남의 프로필·장바구니·주문이 화면에 남는다.
 */
function isSameMember(previous: string | null, next: string): boolean {
  const previousId = memberIdOf(previous);
  const nextId = memberIdOf(next);
  return previousId !== null && nextId !== null && previousId === nextId;
}

export const tokenStore = {
  getAccess: () => localStorage.getItem(ACCESS_KEY),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),

  /**
   * 토큰을 저장한다. <b>회원이 바뀌면 조회 캐시를 통째로 비운다.</b>
   *
   * <p>비우지 않으면 다른 계정으로 로그인해도 staleTime(30초) 동안 이전 회원의 `me`·`cart`·
   * `my-orders` 가 그대로 렌더링된다. 새 응답이 도착하기 전까지 남의 데이터가 노출되는 셈이다.
   *
   * <p>토큰 재발급(reissue)은 같은 회원이라 캐시를 유지한다. 여기서 매번 비우면 조용한 갱신마다
   * 화면 전체가 다시 로딩된다.
   */
  save(accessToken: string, refreshToken?: string) {
    const changedMember = !isSameMember(localStorage.getItem(ACCESS_KEY), accessToken);
    localStorage.setItem(ACCESS_KEY, accessToken);
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken);
    if (changedMember) queryClient.clear();
  },

  /** 토큰을 지운다. (로그아웃 / 재발급 실패) 남은 데이터가 다음 로그인에 새지 않도록 캐시도 비운다. */
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    queryClient.clear();
  },

  isLoggedIn: () => Boolean(localStorage.getItem(ACCESS_KEY))
};
