import { api } from './client';
import type { MemberMeResponse } from './types';

export function getMe() {
  return api<MemberMeResponse>('/api/v1/members/me', { auth: true });
}

export function updateMe(body: { email?: string; nickname?: string }) {
  return api<MemberMeResponse>('/api/v1/members/me', { method: 'PATCH', body, auth: true });
}
