import { FormEvent, useEffect, useMemo, useState } from 'react';
import { apiRequest, ApiResult, toPrettyJson, unwrapData } from './api';
import { requestTossPayment, TossMethod, PAYMENT_METHOD_KEY } from './payment';

type LogEntry = {
  id: string;
  label: string;
  method: string;
  path: string;
  status: number;
  ok: boolean;
  elapsedMs: number;
  body: unknown;
  requestedAt: string;
};

type Product = {
  productId?: number;
  productName?: string;
  productPrice?: number;
  stocks?: number;
};

type LoginResponse = {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
  memberId: number;
  role: string;
  newMember: boolean;
};

type OrderItem = {
  orderItemId?: number;
  productId?: number;
  productName?: string;
  quantity?: number;
  orderPrice?: number;
  itemTotalPrice?: number;
};

type OrderDetail = {
  orderId?: number;
  status?: string;
  totalPrice?: number;
  canceledAt?: string | null;
  createdAt?: string;
  orderItems?: OrderItem[];
};

const TOKEN_KEY = 'soldout_access_token';
const STATE_KEY = 'soldout_oauth_state';
const PRODUCT_CACHE_KEY = 'soldout_products_cache';
const PRODUCT_CACHE_TTL_MS = 60_000;
const FRONT_CALLBACK_URI = `${window.location.origin}/oauth/kakao/callback`;

function getNested<T = unknown>(value: unknown, key: string): T | undefined {
  if (value && typeof value === 'object' && key in value) {
    return (value as Record<string, T>)[key];
  }
  return undefined;
}

function formatPrice(price?: number) {
  return price === undefined ? '가격 확인' : `${price.toLocaleString()}원`;
}

function productInitial(product?: Product | null) {
  return product?.productName?.slice(0, 2).toUpperCase() ?? 'SO';
}

function createLogId() {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function readProductCache(): Product[] {
  const rawCache = sessionStorage.getItem(PRODUCT_CACHE_KEY);
  if (!rawCache) {
    return [];
  }

  try {
    const cache = JSON.parse(rawCache) as { savedAt: number; products: Product[] };
    if (Date.now() - cache.savedAt > PRODUCT_CACHE_TTL_MS) {
      sessionStorage.removeItem(PRODUCT_CACHE_KEY);
      return [];
    }
    return Array.isArray(cache.products) ? cache.products : [];
  } catch {
    sessionStorage.removeItem(PRODUCT_CACHE_KEY);
    return [];
  }
}

function writeProductCache(products: Product[]) {
  sessionStorage.setItem(PRODUCT_CACHE_KEY, JSON.stringify({
    savedAt: Date.now(),
    products
  }));
}

export default function App() {
  const cachedProducts = readProductCache();
  const [routePath, setRoutePath] = useState(window.location.pathname);
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY) ?? '');
  const [state, setState] = useState(() => localStorage.getItem(STATE_KEY) ?? '');
  const [code, setCode] = useState('');
  const [member, setMember] = useState<unknown>(null);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(12);
  const [products, setProducts] = useState<Product[]>(cachedProducts);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(cachedProducts[0] ?? null);
  const [orderDetail, setOrderDetail] = useState<OrderDetail | null>(null);
  const [productId, setProductId] = useState(cachedProducts[0]?.productId ? String(cachedProducts[0].productId) : '');
  const [quantity, setQuantity] = useState(1);
  const [paymentMethod, setPaymentMethod] = useState('CARD');
  const [stockQuantity, setStockQuantity] = useState(10);
  const [stockReason, setStockReason] = useState('관리자 입고');
  const [orderId, setOrderId] = useState('');
  const [customMethod, setCustomMethod] = useState('GET');
  const [customPath, setCustomPath] = useState('/api/v1/products');
  const [customBody, setCustomBody] = useState('{\n  "quantity": 1\n}');
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [notice, setNotice] = useState('인기 상품을 둘러보고 바로 구매해보세요.');
  const [isLoading, setIsLoading] = useState(false);

  const isAdmin = routePath.startsWith('/admin');
  const isCheckout = routePath.startsWith('/checkout/');
  const isOrderPage = routePath.startsWith('/orders/');
  const isPaymentSuccess = routePath === '/payment/success';
  const isPaymentFail = routePath === '/payment/fail';
  const checkoutProductId = isCheckout ? routePath.split('/')[2] : '';
  const routeOrderId = isOrderPage ? routePath.split('/')[2] : '';
  const visibleProducts = products;

  const memberLabel = useMemo(() => {
    if (!token) {
      return 'Guest';
    }
    const nickname = getNested<string>(member, 'nickname');
    const email = getNested<string>(member, 'email');
    return nickname || email || 'Member';
  }, [member, token]);

  useEffect(() => {
    const handlePopState = () => setRoutePath(window.location.pathname);
    window.addEventListener('popstate', handlePopState);

    const params = new URLSearchParams(window.location.search);
    const callbackCode = params.get('code');
    const callbackState = params.get('state');

    if (window.location.pathname === '/oauth/kakao/callback' && callbackCode && callbackState) {
      setCode(callbackCode);
      setState(callbackState);
      localStorage.setItem(STATE_KEY, callbackState);
      completeKakaoLogin(callbackCode, callbackState, FRONT_CALLBACK_URI);
    }

    // 토스 결제 성공 리다이렉트 → 백엔드에 결제 승인 요청
    if (window.location.pathname === '/payment/success') {
      void confirmTossPayment();
    }

    void getProducts(undefined, { quiet: true });

    if (checkoutProductId) {
      setProductId(checkoutProductId);
      void loadCheckoutProduct(checkoutProductId);
    }

    if (routeOrderId) {
      setOrderId(routeOrderId);
      void loadOrder(routeOrderId);
    }

    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  function navigate(path: string) {
    window.history.pushState(null, '', path);
    setRoutePath(path);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function saveToken(nextToken: string) {
    setToken(nextToken);
    localStorage.setItem(TOKEN_KEY, nextToken);
  }

  function clearSession() {
    setToken('');
    setMember(null);
    localStorage.removeItem(TOKEN_KEY);
    setNotice('로그아웃되었습니다.');
  }

  async function run<T = unknown>(
    label: string,
    method: string,
    path: string,
    body?: unknown,
    withToken = false,
    tokenOverride?: string
  ): Promise<ApiResult<T>> {
    setIsLoading(true);
    const startedAt = performance.now();
    try {
      const result = await apiRequest<T>(path, {
        method,
        body,
        token: tokenOverride ?? (withToken ? token : undefined)
      });

      setLogs((prev) => [
        {
          id: createLogId(),
          label,
          method,
          path,
          status: result.status,
          ok: result.ok,
          elapsedMs: result.elapsedMs,
          body: result.body,
          requestedAt: new Date().toLocaleTimeString()
        },
        ...prev
      ].slice(0, 14));

      return result;
    } catch (error) {
      const message = error instanceof Error ? error.message : '알 수 없는 오류가 발생했습니다.';
      const result: ApiResult<T> = {
        ok: false,
        status: 0,
        body: {
          success: false,
          data: null,
          errorCode: 'NETWORK_ERROR',
          message
        },
        elapsedMs: Math.round(performance.now() - startedAt)
      };

      setLogs((prev) => [
        {
          id: createLogId(),
          label,
          method,
          path,
          status: result.status,
          ok: result.ok,
          elapsedMs: result.elapsedMs,
          body: result.body,
          requestedAt: new Date().toLocaleTimeString()
        },
        ...prev
      ].slice(0, 14));
      setNotice(`요청에 실패했습니다: ${message}`);

      return result;
    } finally {
      setIsLoading(false);
    }
  }

  async function getKakaoAuthorizeUrl() {
    const result = await run<{ url: string; state: string }>('카카오 인가 URL', 'GET', '/api/v1/auth/kakao/authorize-url');
    const data = unwrapData<{ url: string; state: string }>(result);

    if (data?.state) {
      setState(data.state);
      localStorage.setItem(STATE_KEY, data.state);
    }

    if (data?.url) {
      window.open(data.url, '_blank', 'noopener,noreferrer');
    }
  }

  async function completeKakaoLogin(loginCode = code, loginState = state, redirectUri = FRONT_CALLBACK_URI) {
    const result = await run<LoginResponse>('카카오 로그인 완료', 'POST', '/api/v1/auth/kakao/login', {
      code: loginCode,
      state: loginState,
      redirectUri
    });
    const data = unwrapData<LoginResponse>(result);

    if (data?.accessToken) {
      saveToken(data.accessToken);
      await loadMe(data.accessToken);
      setNotice('로그인되었습니다.');
    }
  }

  async function getMe() {
    await loadMe(token);
  }

  async function loadMe(accessToken: string) {
    const result = await run('내 정보 조회', 'GET', '/api/v1/members/me', undefined, false, accessToken);
    setMember(unwrapData(result));
  }

  async function getProducts(event?: FormEvent, options: { quiet?: boolean } = {}) {
    event?.preventDefault();
    const params = new URLSearchParams({
      page: String(page),
      size: String(size)
    });
    if (keyword.trim()) {
      params.set('keyword', keyword.trim());
    }

    const result = await run<{ content?: Product[] }>('상품 목록 조회', 'GET', `/api/v1/products?${params}`);
    const data = unwrapData<{ content?: Product[] }>(result);
    const nextProducts = Array.isArray(data?.content) ? data.content : [];
    const productsWithStocks = await enrichProductsWithStocks(nextProducts);

    setProducts(productsWithStocks);
    writeProductCache(productsWithStocks);

    if (productsWithStocks[0]) {
      chooseProduct(productsWithStocks[0]);
    }

    if (!options.quiet) {
      setNotice(productsWithStocks.length > 0 ? '상품 목록과 재고를 업데이트했습니다.' : '등록된 상품을 찾지 못했습니다.');
    }
  }

  async function enrichProductsWithStocks(nextProducts: Product[]) {
    return Promise.all(
      nextProducts.map(async (product) => {
        if (!product.productId) {
          return product;
        }

        try {
          const result = await apiRequest(`/api/v1/products/${product.productId}/stock`);
          const stockData = unwrapData(result);
          const stocks = getNested<number>(stockData, 'stocks');

          return stocks === undefined ? product : { ...product, stocks };
        } catch {
          return product;
        }
      })
    );
  }

  function chooseProduct(product: Product) {
    setSelectedProduct(product);
    if (product.productId !== undefined) {
      setProductId(String(product.productId));
    }
  }

  function startCheckout(product: Product) {
    chooseProduct(product);
    setQuantity(1);
    if (product.productId !== undefined) {
      navigate(`/checkout/${product.productId}`);
    }
  }

  async function loadCheckoutProduct(nextProductId = productId) {
    if (!nextProductId) {
      return;
    }

    const detailResult = await run<Product>('구매 상품 상세 조회', 'GET', `/api/v1/products/${nextProductId}`);
    const detail = unwrapData<Product>(detailResult);
    const stockResult = await run('구매 상품 재고 조회', 'GET', `/api/v1/products/${nextProductId}/stock`);
    const stockData = unwrapData(stockResult);
    const stocks = getNested<number>(stockData, 'stocks');

    const nextProduct = {
      ...(detail ?? selectedProduct ?? {}),
      productId: Number(nextProductId),
      stocks
    };
    setSelectedProduct(nextProduct);
    setProductId(nextProductId);
    setNotice(stocks === undefined ? '상품 정보를 불러왔습니다.' : `구매 가능 수량은 최대 ${stocks}개입니다.`);
  }

  async function getProductDetail() {
    const result = await run<Product>('상품 상세 조회', 'GET', `/api/v1/products/${productId}`);
    const data = unwrapData<Product>(result);
    if (data) {
      setSelectedProduct({ ...data, productId: Number(productId) });
      setNotice('상품 상세 정보를 불러왔습니다.');
    }
  }

  async function getStock() {
    const result = await run('재고 조회', 'GET', `/api/v1/products/${productId}/stock`);
    const data = unwrapData(result);
    const stocks = getNested<number>(data, 'stocks');
    if (stocks !== undefined) {
      setSelectedProduct((prev) => ({ ...(prev ?? {}), productId: Number(productId), stocks }));
      setNotice(`현재 재고는 ${stocks}개입니다.`);
    }
  }

  async function stockIn() {
    await run('상품 입고', 'POST', `/api/v1/products/${productId}/stock-in`, {
      quantity: stockQuantity,
      reason: stockReason || null
    });
    setNotice('입고 요청을 처리했습니다.');
  }

  async function purchase() {
    const result = await run('상품 구매', 'POST', `/api/v1/products/${productId}/orders`, {
      quantity
    });
    const data = unwrapData(result);
    const nextOrderId = getNested<number>(data, 'orderId');
    if (nextOrderId) {
      setOrderId(String(nextOrderId));
      setNotice(`주문이 생성되었습니다. 주문 번호는 ${nextOrderId}입니다.`);
      await loadOrder(String(nextOrderId));
      navigate(`/orders/${nextOrderId}`);
    } else if (!result.ok) {
      setNotice('구매에 실패했습니다. 응답 로그를 확인해주세요.');
    }
  }

  async function getOrder() {
    await loadOrder(orderId);
  }

  async function loadOrder(nextOrderId = orderId) {
    if (!nextOrderId) {
      return;
    }

    const result = await run<OrderDetail>('주문 조회', 'GET', `/api/v1/orders/${nextOrderId}`);
    const data = unwrapData<OrderDetail>(result);

    if (data) {
      setOrderDetail(data);
      setOrderId(String(data.orderId ?? nextOrderId));
      setNotice('주문 정보를 불러왔습니다.');
    } else if (!result.ok) {
      setOrderDetail(null);
      setNotice('주문을 찾지 못했습니다.');
    }
  }

  // 주문 상세에서 "결제하기" → 토스 결제창 호출
  async function startPayment() {
    const pk = orderDetail?.orderId ?? Number(routeOrderId);
    const amount = orderDetail?.totalPrice ?? 0;
    if (!pk || amount <= 0) {
      setNotice('결제할 주문 정보가 없습니다.');
      return;
    }
    try {
      await requestTossPayment({
        orderPk: pk,
        amount,
        orderName: `SoldOut 주문 #${pk}`,
        method: paymentMethod as TossMethod
      });
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '결제 요청에 실패했습니다.');
    }
  }

  // 토스 결제 성공 후 successUrl 진입 시 백엔드 승인(confirm) 호출
  async function confirmTossPayment() {
    const params = new URLSearchParams(window.location.search);
    const paymentKey = params.get('paymentKey');
    const tossOrderId = params.get('orderId') ?? '';
    const orderPk = tossOrderId.replace('ORDER_', '');
    const method = sessionStorage.getItem(PAYMENT_METHOD_KEY) ?? 'CARD';

    if (!paymentKey || !orderPk) {
      setNotice('결제 정보가 올바르지 않습니다.');
      return;
    }

    const result = await run('결제 승인', 'POST', `/api/v1/orders/${orderPk}/payments`, {
      paymentKey,
      method
    });

    if (result.ok) {
      setOrderId(orderPk);
      await loadOrder(orderPk);
      setNotice('결제가 완료되었습니다!');
      navigate(`/orders/${orderPk}`);
    } else {
      setNotice('결제 승인에 실패했습니다. 응답 로그를 확인해주세요.');
    }
  }

  // 주문 상세에서 "환불하기" → 백엔드 환불 호출
  async function refundPayment() {
    const pk = orderDetail?.orderId ?? routeOrderId;
    const result = await run('결제 환불', 'POST', `/api/v1/orders/${pk}/payments/refund`, {
      cancelReason: '고객 환불 요청'
    });
    if (result.ok) {
      setNotice('환불이 완료되었습니다.');
      void loadOrder(String(pk));
    }
  }

  async function runCustomApi() {
    let body: unknown = undefined;
    if (!['GET', 'DELETE'].includes(customMethod) && customBody.trim()) {
      try {
        body = JSON.parse(customBody);
      } catch (error) {
        const message = error instanceof Error ? error.message : 'JSON 형식이 올바르지 않습니다.';
        setLogs((prev) => [
          {
            id: createLogId(),
            label: '직접 API 호출',
            method: customMethod,
            path: customPath,
            status: 0,
            ok: false,
            elapsedMs: 0,
            body: {
              success: false,
              data: null,
              errorCode: 'INVALID_JSON',
              message
            },
            requestedAt: new Date().toLocaleTimeString()
          },
          ...prev
        ].slice(0, 14));
        setNotice(`요청 본문 JSON을 확인해주세요: ${message}`);
        return;
      }
    }
    await run('직접 API 호출', customMethod, customPath, body, true);
  }

  if (isAdmin) {
    return (
      <main className="admin-shell">
        <AdminHeader memberLabel={memberLabel} token={token} onLogin={getKakaoAuthorizeUrl} onLogout={clearSession} />

        <section className="admin-hero">
          <div>
            <p className="eyebrow">Admin Console</p>
            <h1>SoldOut 운영 관리</h1>
            <p>상품, 재고, 주문, 인증 흐름을 한 곳에서 확인하는 내부 관리자 화면입니다.</p>
          </div>
          <div className="admin-stat-grid">
            <Metric label="표시 상품" value={String(visibleProducts.length)} />
            <Metric label="최근 주문" value={orderId || '-'} />
            <Metric label="API 로그" value={String(logs.length)} />
          </div>
        </section>

        <section className="admin-grid">
          <article className="admin-card span-2">
            <div className="card-heading">
              <div>
                <p className="eyebrow">Products</p>
                <h2>상품 조회</h2>
              </div>
              <button type="button" onClick={getProducts} disabled={isLoading}>새로고침</button>
            </div>
            <form className="admin-form row" onSubmit={getProducts}>
              <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="상품명 검색" />
              <input type="number" value={page} min={0} onChange={(event) => setPage(Number(event.target.value))} aria-label="페이지" />
              <input type="number" value={size} min={1} onChange={(event) => setSize(Number(event.target.value))} aria-label="크기" />
              <button type="submit" disabled={isLoading}>조회</button>
            </form>
            <div className="admin-table">
              {visibleProducts.length === 0 ? (
                <div className="empty-state">표시할 상품이 없습니다. 백엔드 서버와 DB 상태를 확인한 뒤 새로고침을 눌러주세요.</div>
              ) : visibleProducts.map((product, index) => (
                <button type="button" className="table-row" key={`${product.productId}-${index}`} onClick={() => chooseProduct(product)}>
                  <span>#{product.productId ?? '-'}</span>
                  <strong>{product.productName ?? '상품명 없음'}</strong>
                  <span>{formatPrice(product.productPrice)}</span>
                  <span>{product.stocks === undefined ? '미조회' : `${product.stocks}개`}</span>
                </button>
              ))}
            </div>
          </article>

          <article className="admin-card">
            <div className="card-heading">
              <div>
                <p className="eyebrow">Inventory</p>
                <h2>재고 관리</h2>
              </div>
            </div>
            <div className="admin-form">
              <label>상품 ID<input value={productId} onChange={(event) => setProductId(event.target.value)} /></label>
              <div className="two-col">
                <button type="button" onClick={getProductDetail} disabled={!productId || isLoading}>상세</button>
                <button type="button" onClick={getStock} disabled={!productId || isLoading}>재고</button>
              </div>
              <label>입고 수량<input type="number" min={1} value={stockQuantity} onChange={(event) => setStockQuantity(Number(event.target.value))} /></label>
              <label>입고 사유<input value={stockReason} onChange={(event) => setStockReason(event.target.value)} /></label>
              <button type="button" className="primary" onClick={stockIn} disabled={!productId || isLoading}>입고 처리</button>
            </div>
          </article>

          <article className="admin-card">
            <div className="card-heading">
              <div>
                <p className="eyebrow">Orders</p>
                <h2>주문 관리</h2>
              </div>
            </div>
            <div className="admin-form">
              <label>상품 ID<input value={productId} onChange={(event) => setProductId(event.target.value)} /></label>
              <label>구매 수량<input type="number" min={1} value={quantity} onChange={(event) => setQuantity(Number(event.target.value))} /></label>
              <button type="button" className="primary" onClick={purchase} disabled={!productId || isLoading}>구매 생성</button>
              <label>주문 ID<input value={orderId} onChange={(event) => setOrderId(event.target.value)} /></label>
              <button type="button" onClick={getOrder} disabled={!orderId || isLoading}>주문 조회</button>
            </div>
          </article>

          <article className="admin-card">
            <div className="card-heading">
              <div>
                <p className="eyebrow">Auth</p>
                <h2>인증</h2>
              </div>
            </div>
            <div className="admin-form">
              <button type="button" onClick={getKakaoAuthorizeUrl} disabled={isLoading}>카카오 로그인 URL</button>
              <button type="button" onClick={getMe} disabled={!token || isLoading}>내 정보 조회</button>
              <label>인가 코드<input value={code} onChange={(event) => setCode(event.target.value)} /></label>
              <label>state<input value={state} onChange={(event) => setState(event.target.value)} /></label>
              <button type="button" onClick={() => completeKakaoLogin()} disabled={!code || !state || isLoading}>JWT 발급</button>
              <label>JWT<textarea value={token} onChange={(event) => saveToken(event.target.value.trim())} rows={4} /></label>
            </div>
          </article>

          <article className="admin-card span-2">
            <div className="card-heading">
              <div>
                <p className="eyebrow">API</p>
                <h2>직접 호출</h2>
              </div>
            </div>
            <div className="custom-api-row">
              <select value={customMethod} onChange={(event) => setCustomMethod(event.target.value)}>
                <option>GET</option>
                <option>POST</option>
                <option>PUT</option>
                <option>PATCH</option>
                <option>DELETE</option>
              </select>
              <input value={customPath} onChange={(event) => setCustomPath(event.target.value)} />
              <button type="button" onClick={runCustomApi} disabled={!customPath || isLoading}>실행</button>
            </div>
            <textarea value={customBody} onChange={(event) => setCustomBody(event.target.value)} rows={6} />
          </article>

          <article className="admin-card span-2">
            <div className="card-heading">
              <div>
                <p className="eyebrow">Logs</p>
                <h2>응답 로그</h2>
              </div>
              <button type="button" onClick={() => setLogs([])}>비우기</button>
            </div>
            <LogList logs={logs} />
          </article>
        </section>
      </main>
    );
  }

  if (isCheckout) {
    const currentStock = selectedProduct?.stocks;
    const maxQuantity = currentStock === undefined ? undefined : Math.max(currentStock, 1);
    const totalPrice = (selectedProduct?.productPrice ?? 0) * quantity;
    const canPurchase = Boolean(productId) && quantity > 0 && (currentStock === undefined || currentStock >= quantity);

    return (
      <main className="store-shell">
        <header className="store-header">
          <a className="brand" href="/" onClick={(event) => { event.preventDefault(); navigate('/'); }}>
            <span className="brand-mark">S</span>
            <span>SoldOut</span>
          </a>
          <nav className="store-nav" aria-label="주요 메뉴">
            <a href="/" onClick={(event) => { event.preventDefault(); navigate('/'); }}>Products</a>
            <a href="/admin">Admin</a>
          </nav>
          <div className="member-chip">
            <span>{memberLabel}</span>
            {token ? (
              <button type="button" onClick={clearSession}>로그아웃</button>
            ) : (
              <button type="button" onClick={getKakaoAuthorizeUrl} disabled={isLoading}>카카오 로그인</button>
            )}
          </div>
        </header>

        <section className="checkout-page">
          <button type="button" className="back-link" onClick={() => navigate('/')}>상품 목록으로 돌아가기</button>

          <div className="checkout-product">
            <div className="checkout-visual">{productInitial(selectedProduct)}</div>
            <div className="checkout-copy">
              <p className="eyebrow">Checkout</p>
              <h1>{selectedProduct?.productName ?? `상품 #${productId}`}</h1>
              <p>{formatPrice(selectedProduct?.productPrice)}</p>
              <div className="checkout-stock">
                <span>구매 가능 수량</span>
                <strong>{currentStock === undefined ? '확인 중' : `${currentStock}개`}</strong>
              </div>
              <button type="button" onClick={() => loadCheckoutProduct(productId)} disabled={!productId || isLoading}>
                상품 정보 새로고침
              </button>
            </div>
          </div>

          <aside className="checkout-order-panel">
            <div>
              <p className="eyebrow">Order Summary</p>
              <h2>구매 정보</h2>
            </div>

            <label>
              수량
              <input
                type="number"
                min={1}
                max={maxQuantity}
                value={quantity}
                onChange={(event) => setQuantity(Number(event.target.value))}
              />
            </label>

            <div className="payment-methods" role="group" aria-label="결제 수단">
              <button type="button" className={paymentMethod === 'CARD' ? 'selected' : ''} onClick={() => setPaymentMethod('CARD')}>
                카드/간편결제
              </button>
              <button type="button" className={paymentMethod === 'TRANSFER' ? 'selected' : ''} onClick={() => setPaymentMethod('TRANSFER')}>
                계좌 이체
              </button>
            </div>

            <div className="price-summary">
              <span>상품 금액</span>
              <strong>{formatPrice(selectedProduct?.productPrice)}</strong>
              <span>수량</span>
              <strong>{quantity}개</strong>
              <span>총 결제 예정 금액</span>
              <strong>{totalPrice > 0 ? formatPrice(totalPrice) : '가격 확인'}</strong>
            </div>

            <button type="button" className="primary" onClick={purchase} disabled={!canPurchase || isLoading}>
              구매 확정
            </button>

            {currentStock !== undefined && currentStock < quantity ? (
              <p className="checkout-warning">선택한 수량이 현재 재고보다 많습니다.</p>
            ) : null}

            {orderId ? (
              <div className="order-created">
                <span>생성된 주문</span>
                <strong>#{orderId}</strong>
                <button type="button" onClick={() => navigate(`/orders/${orderId}`)}>주문 상세 보기</button>
              </div>
            ) : null}
          </aside>
        </section>
      </main>
    );
  }

  if (isPaymentSuccess) {
    return (
      <main className="store-shell">
        <section className="order-page">
          <div className="order-hero">
            <div>
              <p className="eyebrow">Payment</p>
              <h1>결제 처리 중…</h1>
              <p>토스 결제 승인을 확인하고 있습니다. 잠시만 기다려 주세요.</p>
            </div>
          </div>
        </section>
      </main>
    );
  }

  if (isPaymentFail) {
    const failParams = new URLSearchParams(window.location.search);
    return (
      <main className="store-shell">
        <section className="order-page">
          <button type="button" className="back-link" onClick={() => navigate('/')}>상품 목록으로 돌아가기</button>
          <div className="order-hero">
            <div>
              <p className="eyebrow">Payment</p>
              <h1>결제에 실패했습니다</h1>
              <p>{failParams.get('message') ?? '결제가 취소되었거나 실패했습니다.'}</p>
            </div>
          </div>
        </section>
      </main>
    );
  }

  if (isOrderPage) {
    const items = orderDetail?.orderItems ?? [];

    return (
      <main className="store-shell">
        <header className="store-header">
          <a className="brand" href="/" onClick={(event) => { event.preventDefault(); navigate('/'); }}>
            <span className="brand-mark">S</span>
            <span>SoldOut</span>
          </a>
          <nav className="store-nav" aria-label="주요 메뉴">
            <a href="/" onClick={(event) => { event.preventDefault(); navigate('/'); }}>Products</a>
            <a href="/admin">Admin</a>
          </nav>
          <div className="member-chip">
            <span>{memberLabel}</span>
            {token ? (
              <button type="button" onClick={clearSession}>로그아웃</button>
            ) : (
              <button type="button" onClick={getKakaoAuthorizeUrl} disabled={isLoading}>카카오 로그인</button>
            )}
          </div>
        </header>

        <section className="order-page">
          <button type="button" className="back-link" onClick={() => navigate('/')}>상품 목록으로 돌아가기</button>

          <div className="order-hero">
            <div>
              <p className="eyebrow">Order Detail</p>
              <h1>주문 #{orderDetail?.orderId ?? routeOrderId}</h1>
              <p>{orderDetail ? '주문 생성 내역과 상품 구성을 확인할 수 있습니다.' : '주문 정보를 불러오는 중입니다.'}</p>
            </div>
            <div className="order-status-card">
              <span>주문 상태</span>
              <strong>{orderDetail?.status ?? '조회 중'}</strong>
              <p>{orderDetail?.createdAt ?? '-'}</p>
            </div>
          </div>

          <div className="order-layout">
            <article className="order-card">
              <div className="card-heading">
                <div>
                  <p className="eyebrow">Items</p>
                  <h2>주문 상품</h2>
                </div>
                <button type="button" onClick={() => loadOrder(routeOrderId || orderId)} disabled={isLoading}>새로고침</button>
              </div>

              {items.length === 0 ? (
                <div className="empty-state">표시할 주문 상품이 없습니다.</div>
              ) : (
                <div className="order-items">
                  {items.map((item, index) => (
                    <div className="order-item-row" key={`${item.orderItemId}-${index}`}>
                      <div className="mini-visual">{item.productName?.slice(0, 2).toUpperCase() ?? 'SO'}</div>
                      <div>
                        <strong>{item.productName ?? `상품 #${item.productId ?? '-'}`}</strong>
                        <span>수량 {item.quantity ?? '-'}개 · 단가 {formatPrice(item.orderPrice)}</span>
                      </div>
                      <p>{formatPrice(item.itemTotalPrice)}</p>
                    </div>
                  ))}
                </div>
              )}
            </article>

            <aside className="order-summary-card">
              <p className="eyebrow">Summary</p>
              <h2>결제 요약</h2>
              <div className="price-summary">
                <span>주문 번호</span>
                <strong>#{orderDetail?.orderId ?? routeOrderId}</strong>
                <span>주문 상태</span>
                <strong>{orderDetail?.status ?? '-'}</strong>
                <span>총 주문 금액</span>
                <strong>{formatPrice(orderDetail?.totalPrice)}</strong>
              </div>

              {orderDetail?.status === 'PENDING_PAYMENT' ? (
                <>
                  <div className="payment-methods" role="group" aria-label="결제 수단">
                    <button type="button" className={paymentMethod === 'CARD' ? 'selected' : ''} onClick={() => setPaymentMethod('CARD')}>카드/간편결제</button>
                    <button type="button" className={paymentMethod === 'TRANSFER' ? 'selected' : ''} onClick={() => setPaymentMethod('TRANSFER')}>계좌이체</button>
                  </div>
                  <button type="button" className="primary" onClick={startPayment} disabled={isLoading}>결제하기</button>
                </>
              ) : null}

              {orderDetail?.status === 'COMPLETED' ? (
                <button type="button" className="primary" onClick={refundPayment} disabled={isLoading}>환불하기</button>
              ) : null}

              <button type="button" onClick={() => navigate('/')}>계속 쇼핑하기</button>
            </aside>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="store-shell">
      <header className="store-header">
        <a className="brand" href="/">
          <span className="brand-mark">S</span>
          <span>SoldOut</span>
        </a>
        <nav className="store-nav" aria-label="주요 메뉴">
          <a href="#drops">Drops</a>
          <a href="#products">Products</a>
          <a href="/admin">Admin</a>
        </nav>
        <div className="member-chip">
          <span>{memberLabel}</span>
          {token ? (
            <button type="button" onClick={clearSession}>로그아웃</button>
          ) : (
            <button type="button" onClick={getKakaoAuthorizeUrl} disabled={isLoading}>카카오 로그인</button>
          )}
        </div>
      </header>

      <section className="store-hero" id="drops">
        <div className="hero-copy">
          <p className="eyebrow">Limited Drop</p>
          <h1>놓치면 품절되는 상품을 가장 먼저 만나보세요.</h1>
          <p>실시간 재고 확인부터 구매 생성까지, SoldOut MVP의 핵심 구매 흐름을 실제 쇼핑몰처럼 확인할 수 있습니다.</p>
          <div className="hero-actions">
            <button type="button" className="primary" onClick={getProducts} disabled={isLoading}>오늘의 상품 보기</button>
            <button type="button" onClick={getStock} disabled={!productId || isLoading}>선택 상품 재고 확인</button>
          </div>
          <div className="notice-line">{notice}</div>
        </div>
        <div className="drop-card">
          <div className="drop-visual">
            <span>{productInitial(selectedProduct)}</span>
          </div>
          <div className="drop-info">
            <p>Featured</p>
            <h2>{selectedProduct?.productName ?? '오늘의 상품'}</h2>
            <strong>{formatPrice(selectedProduct?.productPrice)}</strong>
            <span>남은 재고 {selectedProduct?.stocks ?? '확인 전'}</span>
          </div>
        </div>
      </section>

      <section className="product-section" id="products">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Shop</p>
            <h2>지금 구매 가능한 상품</h2>
          </div>
          <form className="store-search" onSubmit={getProducts}>
            <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="상품 검색" />
            <button type="submit" disabled={isLoading}>검색</button>
          </form>
        </div>

        <div className="store-grid">
          {visibleProducts.length === 0 ? (
            <div className="empty-state store-empty">아직 표시할 상품이 없습니다. 백엔드 서버가 실행 중이면 잠시 뒤 다시 조회해보세요.</div>
          ) : visibleProducts.map((product, index) => (
            <article className="store-card" key={`${product.productId}-${product.productName}-${index}`}>
              <button type="button" className={`store-product-visual tone-${index % 4}`} onClick={() => chooseProduct(product)}>
                {productInitial(product)}
              </button>
              <div className="store-product-body">
                <span>Limited Stock</span>
                <h3>{product.productName ?? '상품명 없음'}</h3>
                <p>{formatPrice(product.productPrice)}</p>
                <small>{product.stocks === undefined ? '재고 확인 중' : `남은 재고 ${product.stocks}개`}</small>
              </div>
              <div className="store-card-footer">
                <button type="button" onClick={() => chooseProduct(product)}>담기</button>
                <button type="button" className="primary" onClick={() => startCheckout(product)}>
                  구매 선택
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>

    </main>
  );
}

function AdminHeader({
  memberLabel,
  token,
  onLogin,
  onLogout
}: {
  memberLabel: string;
  token: string;
  onLogin: () => void;
  onLogout: () => void;
}) {
  return (
    <header className="admin-header">
      <a className="brand" href="/">
        <span className="brand-mark">S</span>
        <span>SoldOut Admin</span>
      </a>
      <nav className="store-nav" aria-label="관리 메뉴">
        <a href="/">Store</a>
        <a href="/admin">Admin</a>
      </nav>
      <div className="member-chip">
        <span>{memberLabel}</span>
        {token ? <button type="button" onClick={onLogout}>로그아웃</button> : <button type="button" onClick={onLogin}>로그인</button>}
      </div>
    </header>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function LogList({ logs }: { logs: LogEntry[] }) {
  if (logs.length === 0) {
    return <p className="empty-log">아직 요청 로그가 없습니다.</p>;
  }

  return (
    <div className="logs">
      {logs.map((log) => (
        <article className="log-entry" key={log.id}>
          <div className="log-header">
            <strong>{log.label}</strong>
            <span className={log.ok ? 'code ok' : 'code fail'}>{log.status}</span>
          </div>
          <p>{log.method} {log.path} · {log.elapsedMs}ms · {log.requestedAt}</p>
          <pre>{toPrettyJson(log.body)}</pre>
        </article>
      ))}
    </div>
  );
}
