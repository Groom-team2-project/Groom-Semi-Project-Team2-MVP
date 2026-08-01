import { tokenStore } from '../lib/auth';
import type { CommonResponse, TokenReissueResponse } from './types';

// 백엔드 에러를 화면에서 구분 처리할 수 있게 errorCode를 포함하는 에러 타입
export class ApiError extends Error {
  constructor(
    public status: number,
    public errorCode: string | null,
    message: string
  ) {
    super(message);
  }
}

type Options = {
  method?: string;
  body?: unknown;
  auth?: boolean; // true면 Authorization 헤더 첨부
};

async function rawRequest(path: string, options: Options): Promise<Response> {
  const headers: HeadersInit = {};
  const isFormData = options.body instanceof FormData;
  if (options.body !== undefined && !isFormData) headers['Content-Type'] = 'application/json';
  if (options.auth) {
    const token = tokenStore.getAccess();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }
  const requestBody: BodyInit | undefined = options.body === undefined
    ? undefined
    : isFormData
      ? options.body as FormData
      : JSON.stringify(options.body);
  return fetch(path, {
    method: options.method ?? 'GET',
    headers,
    credentials: 'include',
    body: requestBody
  });
}

// 401이면 refresh 토큰으로 access 토큰을 1회 재발급하고 원 요청을 재시도한다
async function tryReissue(): Promise<boolean> {
  const refreshToken = tokenStore.getRefresh();
  if (!refreshToken) return false;
  try {
    const res = await rawRequest('/api/v1/auth/reissue', {
      method: 'POST',
      body: { refreshToken }
    });
    if (!res.ok) return false;
    const json = (await res.json()) as CommonResponse<TokenReissueResponse>;
    if (!json.data) return false;
    tokenStore.save(json.data.accessToken, json.data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

export async function api<T>(path: string, options: Options = {}): Promise<T> {
  let res = await rawRequest(path, options);

  if (res.status === 401 && options.auth) {
    const reissued = await tryReissue();
    if (reissued) {
      res = await rawRequest(path, options);
    } else {
      tokenStore.clear();
    }
  }

  // 204 No Content (이미지 삭제 등)
  if (res.status === 204) return undefined as T;

  let json: CommonResponse<T> | null = null;
  try {
    json = (await res.json()) as CommonResponse<T>;
  } catch {
    // JSON이 아닌 응답 (프록시 에러 등)
  }

  if (!res.ok) {
    throw new ApiError(
      res.status,
      json?.errorCode ?? null,
      json?.message ?? `요청에 실패했습니다 (${res.status})`
    );
  }
  return json?.data as T;
}
