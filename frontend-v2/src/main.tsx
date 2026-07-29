import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';

import './styles/global.css';
import { queryClient } from './lib/queryClient';
import { ToastProvider } from './components/Toast';
import { Layout } from './components/Layout';
import { HomePage } from './pages/HomePage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { CartPage } from './pages/CartPage';
import { OrderDetailPage } from './pages/OrderDetailPage';
import { PaymentSuccessPage } from './pages/PaymentSuccessPage';
import { PaymentFailPage } from './pages/PaymentFailPage';
import { MyPage } from './pages/MyPage';
import { KakaoCallbackPage } from './pages/KakaoCallbackPage';
import { AdminProductsPage } from './pages/admin/AdminProductsPage';
import { AdminCategoriesPage } from './pages/admin/AdminCategoriesPage';
import { AdminStockPage } from './pages/admin/AdminStockPage';

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/products/:productId', element: <ProductDetailPage /> },
      { path: '/cart', element: <CartPage /> },
      { path: '/orders/:orderId', element: <OrderDetailPage /> },
      { path: '/payment/success', element: <PaymentSuccessPage /> },
      { path: '/payment/fail', element: <PaymentFailPage /> },
      { path: '/me', element: <MyPage /> },
      { path: '/oauth/kakao/callback', element: <KakaoCallbackPage /> },
      { path: '/admin', element: <AdminProductsPage /> },
      { path: '/admin/categories', element: <AdminCategoriesPage /> },
      { path: '/admin/stock', element: <AdminStockPage /> }
    ]
  }
]);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>
  </StrictMode>
);
