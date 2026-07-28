import { api } from './client';
import type { KakaoAuthorizeUrlResponse, LoginResponse } from './types';
import { tokenStore } from '../lib/auth';

// 프론트가 받을 카카오 콜백 주소 — 카카오 콘솔에 등록된 URI와 일치해야 한다
export const KAKAO_CALLBACK_URI = `${window.location.origin}/oauth/kakao/callback`;
const STATE_KEY = 'soldout_oauth_state';

export async function startKakaoLogin(): Promise<void> {
  const data = await api<KakaoAuthorizeUrlResponse>('/api/v1/auth/kakao/authorize-url');
  sessionStorage.setItem(STATE_KEY, data.state);
  // 백엔드가 준 인가 URL의 redirect_uri를 프론트 콜백으로 교체
  const url = new URL(data.url);
  url.searchParams.set('redirect_uri', KAKAO_CALLBACK_URI);
  window.location.href = url.toString();
}

export async function completeKakaoLogin(code: string, state: string): Promise<LoginResponse> {
  const data = await api<LoginResponse>('/api/v1/auth/kakao/login', {
    method: 'POST',
    body: { code, state, redirectUri: KAKAO_CALLBACK_URI }
  });
  tokenStore.save(data.accessToken, data.refreshToken);
  return data;
}

export async function logout(): Promise<void> {
  const refreshToken = tokenStore.getRefresh();
  try {
    if (refreshToken) {
      await api<void>('/api/v1/auth/logout', { method: 'POST', body: { refreshToken }, auth: true });
    }
  } finally {
    tokenStore.clear();
  }
}
