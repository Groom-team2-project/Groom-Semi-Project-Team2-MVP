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
      <footer className="footer">
        <div className="footer-inner">
          <div>
            <div className="brand">SOLDOUT</div>
            <p>
              한정판 드랍 커머스 플랫폼.
              <br />
              실시간 재고 예약과 안전한 결제로 원하는 드랍을 놓치지 마세요.
            </p>
          </div>
          <div>
            <h4>Shop</h4>
            <ul>
              <li><NavLink to="/">전체 드랍</NavLink></li>
              <li><NavLink to="/cart">장바구니</NavLink></li>
              <li><NavLink to="/me">마이페이지</NavLink></li>
            </ul>
          </div>
          <div>
            <h4>Support</h4>
            <ul>
              <li>고객센터 1544-0000</li>
              <li>평일 10:00 - 18:00</li>
              <li>Team SoldOut, 2026</li>
            </ul>
          </div>
        </div>
      </footer>
    </>
  );
}
