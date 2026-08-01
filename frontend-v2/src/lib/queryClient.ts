import { QueryClient } from '@tanstack/react-query';

/**
 * 앱 전역에서 공유하는 QueryClient.
 *
 * main.tsx 안에서 만들면 컴포넌트가 아닌 모듈(api/auth.ts, lib/auth.ts)에서 접근할 수 없다.
 * 로그인·로그아웃은 컴포넌트 밖에서 일어나는데 그 시점에 캐시를 비워야 하므로 여기로 뺐다.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000, refetchOnWindowFocus: false }
  }
});
