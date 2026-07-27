import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { completeKakaoLogin } from '../api/auth';
import { useToast } from '../components/Toast';

export function KakaoCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [error, setError] = useState<string | null>(null);
  const requested = useRef(false);

  useEffect(() => {
    if (requested.current) return;
    requested.current = true;

    const code = params.get('code');
    const state = params.get('state');
    if (!code || !state) {
      setError('카카오 인증 정보가 없어요.');
      return;
    }

    completeKakaoLogin(code, state)
      .then((res) => {
        toast(res.newMember ? '가입을 환영해요! 🎉' : '로그인되었어요.');
        navigate('/', { replace: true });
      })
      .catch(() => setError('로그인에 실패했어요. 다시 시도해주세요.'));
  }, [params, navigate, toast]);

  return (
    <div style={{ textAlign: 'center', paddingTop: 80 }} className="rise">
      {error ? (
        <>
          <h1 className="h-section">{error}</h1>
          <button className="btn btn-primary" style={{ marginTop: 20 }} onClick={() => navigate('/')}>
            홈으로
          </button>
        </>
      ) : (
        <>
          <div className="spin" />
          <p className="text-muted">카카오 로그인 처리 중…</p>
        </>
      )}
    </div>
  );
}
