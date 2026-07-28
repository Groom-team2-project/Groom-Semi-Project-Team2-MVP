import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getMe, updateMe } from '../api/members';
import { ApiError } from '../api/client';
import { useToast } from '../components/Toast';
import { tokenStore } from '../lib/auth';
import { startKakaoLogin } from '../api/auth';

export function MyPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const loggedIn = tokenStore.isLoggedIn();
  const [nickname, setNickname] = useState('');

  const { data: me, isLoading } = useQuery({
    queryKey: ['me'],
    queryFn: getMe,
    enabled: loggedIn
  });

  const updateMutation = useMutation({
    mutationFn: () => updateMe({ nickname: nickname.trim() }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me'] });
      toast('프로필이 수정되었어요.');
      setNickname('');
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : '수정에 실패했어요.', 'error')
  });

  if (!loggedIn) {
    return (
      <div className="empty">
        <p>로그인 후 이용할 수 있어요.</p>
        <button className="btn btn-primary" style={{ marginTop: 16 }} onClick={() => startKakaoLogin()}>
          카카오로 로그인
        </button>
      </div>
    );
  }
  if (isLoading) return <div className="spin" />;

  return (
    <>
      <header style={{ marginBottom: 32 }} className="rise">
        <span className="eyebrow">My Page</span>
        <h1 className="h-display" style={{ fontSize: 'clamp(28px,4vw,40px)' }}>
          {me?.nickname ?? '회원'}님, 반가워요
        </h1>
      </header>

      <div className="bezel rise rise-1" style={{ maxWidth: 520 }}>
        <div className="core stack">
          <div className="row between">
            <span className="text-muted">이메일</span>
            <b style={{ fontSize: 14 }}>{me?.email ?? '-'}</b>
          </div>
          <div className="row between">
            <span className="text-muted">닉네임</span>
            <b style={{ fontSize: 14 }}>{me?.nickname ?? '-'}</b>
          </div>
          <div className="row between">
            <span className="text-muted">가입 경로</span>
            <b style={{ fontSize: 14 }}>{me?.provider ?? '-'}</b>
          </div>
          <div className="divider" style={{ margin: '8px 0' }} />
          <form
            className="row"
            onSubmit={(e) => {
              e.preventDefault();
              if (!nickname.trim()) return toast('새 닉네임을 입력해주세요.', 'error');
              updateMutation.mutate();
            }}
          >
            <input
              className="input"
              style={{ flex: 1 }}
              placeholder="새 닉네임"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
            />
            <button className="btn btn-primary btn-sm" disabled={updateMutation.isPending}>변경</button>
          </form>
          <p className="text-muted" style={{ fontSize: 12 }}>
            주문 내역 기능은 준비 중이에요. 결제 완료 메일에서 주문 번호를 확인할 수 있어요.
          </p>
        </div>
      </div>
    </>
  );
}
