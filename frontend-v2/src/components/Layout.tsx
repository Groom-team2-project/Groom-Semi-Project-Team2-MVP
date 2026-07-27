import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { tokenStore } from '../lib/auth';
import { startKakaoLogin, logout } from '../api/auth';
import { useToast } from './Toast';

export function Layout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const loggedIn = tokenStore.isLoggedIn();

  async function handleLogout() {
    await logout();
    queryClient.clear();
    toast('로그아웃되었습니다.');
    navigate('/');
  }

  return (
    <>
      <div className="nav-wrap">
        <nav className="nav">
          <NavLink to="/" className="nav-brand">SOLDOUT</NavLink>
          <div className="nav-links">
            <NavLink to="/" end>Shop</NavLink>
            <NavLink to="/cart" className="keep">Cart</NavLink>
            <NavLink to="/me">My</NavLink>
            <NavLink to="/admin">Admin</NavLink>
            {loggedIn ? (
              <button onClick={handleLogout}>로그아웃</button>
            ) : (
              <button className="keep" onClick={() => startKakaoLogin().catch(() => toast('로그인 시작에 실패했어요.', 'error'))}>
                로그인
              </button>
            )}
          </div>
        </nav>
      </div>
      <main className="shell page">
        <Outlet />
      </main>
    </>
  );
}
