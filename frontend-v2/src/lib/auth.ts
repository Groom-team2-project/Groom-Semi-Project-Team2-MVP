// JWT 토큰 보관소 — localStorage 기반 (백엔드가 Bearer 토큰 방식)

import { queryClient } from './queryClient';

const ACCESS_KEY = 'soldout_access_token';
const REFRESH_KEY = 'soldout_refresh_token';

/**
 * 이 탭의 QueryClient 안에 들어 있는 개인 데이터가 <b>누구 것인지</b>.
 *
 * <p>토큰이 아니라 <b>캐시</b>를 기준으로 판단해야 한다. localStorage 는 다른 탭이나 개발자 도구가
 * 언제든 지울 수 있는데, 그렇다고 이 탭의 캐시가 함께 비워지지는 않는다. 저장된 토큰만 보고
 * "이전 회원이 없었다"고 판단하면, 다른 탭에서 로그아웃한 뒤 다른 계정으로 들어왔을 때
 * 앞 회원의 `me`·`cart`·`my-orders` 가 그대로 노출된다.
 *
 * <p>{@code null} 은 "개인 데이터를 캐시한 적이 없다"는 뜻이다. (비로그인 상태로 둘러본 상품 목록
 * 정도만 들어 있으므로 지울 필요가 없다)
 */
let cacheOwnerId: number | null = null;

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
 * 캐시에 남은 개인 데이터를 버린다.
 *
 * <p>진행 중인 요청을 먼저 취소한다. 취소하지 않으면 이전 회원 기준으로 날아간 응답이 비운 캐시에
 * 뒤늦게 내려앉아, 방금 지운 데이터가 새 회원 화면에 되살아날 수 있다.
 */
function dropCachedMemberData() {
  queryClient.cancelQueries();
  queryClient.clear();
  cacheOwnerId = null;
}

export const tokenStore = {
  getAccess: () => localStorage.getItem(ACCESS_KEY),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),

  /**
   * 토큰을 저장한다. <b>캐시 주인이 바뀌면 조회 캐시를 통째로 비운다.</b>
   *
   * <p>비우지 않으면 다른 계정으로 로그인해도 staleTime(30초) 동안 이전 회원의 `me`·`cart`·
   * `my-orders` 가 그대로 렌더링된다. 새 응답이 도착하기 전까지 남의 데이터가 노출되는 셈이다.
   *
   * <p>토큰 재발급(reissue)은 같은 회원이라 캐시를 유지한다. 여기서 매번 비우면 조용한 갱신마다
   * 화면 전체가 다시 로딩된다.
   */
  save(accessToken: string, refreshToken?: string) {
    const nextId = memberIdOf(accessToken);
    // 캐시에 개인 데이터가 있고(주인이 있고) 그 주인이 새 토큰의 회원과 다르면 버린다.
    // 새 토큰을 해독하지 못하면(nextId === null) 같은 사람이라 확신할 수 없으므로 역시 버린다.
    const ownerChanged = cacheOwnerId !== null && cacheOwnerId !== nextId;

    localStorage.setItem(ACCESS_KEY, accessToken);
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken);

    if (ownerChanged) dropCachedMemberData();
    cacheOwnerId = nextId;
  },

  /**
   * 토큰을 지운다. (로그아웃 / 재발급 실패) 남은 데이터가 다음 로그인에 새지 않도록 캐시도 비운다.
   *
   * <p><b>캐시에 주인이 있을 때만 비운다.</b> 이 메서드는 401 응답을 받은 요청 안에서도
   * 불린다({@code api/client.ts}). 무조건 비우면 캐시 초기화 → 마운트된 쿼리 재조회 → 또 401 →
   * 다시 초기화가 맞물려 요청이 끊임없이 반복될 수 있다. 두 번째 호출부터는 주인이 이미 없으므로
   * 여기서 고리가 끊긴다.
   */
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    if (cacheOwnerId !== null) dropCachedMemberData();
  },

  isLoggedIn: () => Boolean(localStorage.getItem(ACCESS_KEY))
};

/**
 * 다른 탭의 로그아웃·계정 전환을 따라간다.
 *
 * <p>localStorage 변경은 <b>다른</b> 탭에만 storage 이벤트로 전달된다. 이 신호가 없으면 A 탭에서
 * 로그아웃해도 B 탭은 이전 회원의 캐시를 들고 계속 화면에 뿌린다.
 */
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key !== ACCESS_KEY) return;
    const nextId = memberIdOf(event.newValue);
    if (cacheOwnerId !== null && cacheOwnerId !== nextId) {
      dropCachedMemberData();
    }
    cacheOwnerId = nextId;
  });
}
