// JWT 토큰 보관소 — localStorage 기반 (백엔드가 Bearer 토큰 방식)

const ACCESS_KEY = 'soldout_access_token';
const REFRESH_KEY = 'soldout_refresh_token';

export const tokenStore = {
  getAccess: () => localStorage.getItem(ACCESS_KEY),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),
  save(accessToken: string, refreshToken?: string) {
    localStorage.setItem(ACCESS_KEY, accessToken);
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
  isLoggedIn: () => Boolean(localStorage.getItem(ACCESS_KEY))
};
