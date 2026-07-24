export type CommonResponse<T = unknown> = {
  success: boolean;
  data: T | null;
  errorCode: string | null;
  message: string | null;
};

export type ApiResult<T = unknown> = {
  ok: boolean;
  status: number;
  body: CommonResponse<T> | unknown;
  elapsedMs: number;
};

type RequestOptions = {
  method?: string;
  token?: string;
  body?: unknown;
};

export async function apiRequest<T = unknown>(
  path: string,
  options: RequestOptions = {}
): Promise<ApiResult<T>> {
  const headers = new Headers();
  const method = options.method ?? 'GET';

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }

  if (options.token) {
    headers.set('Authorization', `Bearer ${options.token}`);
  }

  const startedAt = performance.now();
  const response = await fetch(path, {
    method,
    headers,
    credentials: 'include',
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });
  const elapsedMs = Math.round(performance.now() - startedAt);
  const text = await response.text();

  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }

  return {
    ok: response.ok,
    status: response.status,
    body,
    elapsedMs
  };
}

export function unwrapData<T = unknown>(result: ApiResult<T>): T | null {
  const body = result.body as CommonResponse<T>;
  if (body && typeof body === 'object' && 'data' in body) {
    return body.data;
  }
  return null;
}

export function toPrettyJson(value: unknown): string {
  if (value === undefined) {
    return '';
  }

  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}
