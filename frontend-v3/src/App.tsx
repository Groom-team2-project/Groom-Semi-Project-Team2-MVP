import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { loadTossPayments } from '@tosspayments/tosspayments-sdk';
import { api, ApiError, auth, date, money, store, type Cart, type Category, type MemberCoupon, type Order, type Page, type Product, type ProductDetail, type Review } from './api';

type View = 'shop' | 'detail' | 'cart' | 'order' | 'my' | 'event' | 'admin-products' | 'admin-categories' | 'admin-stock' | 'admin-coupons';
type Notice = { id: number; text: string; error?: boolean };
const imageFallback = (id: number) => `https://picsum.photos/seed/archive-${id}/900/1100`;
const input = (form: HTMLFormElement, name: string) => String(new FormData(form).get(name) ?? '').trim();
const kakaoCallbackUri = () => `${location.origin}/oauth/kakao/callback`;

export default function App() {
  const [view, setView] = useState<View>('shop');
  const [selectedId, setSelectedId] = useState<number>();
  const [notices, setNotices] = useState<Notice[]>([]);
  const [session, setSession] = useState(auth.loggedIn());
  const callbackRan = useRef(false);

  const notify = useCallback((text: string, error = false) => {
    const id = Date.now();
    setNotices((n) => [...n, { id, text, error }]);
    window.setTimeout(() => setNotices((n) => n.filter((item) => item.id !== id)), 3500);
  }, []);

  const go = (next: View, id?: number) => {
    setView(next);
    setSelectedId(id);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (callbackRan.current) return;
    if (params.has('code') && params.has('state')) {
      callbackRan.current = true;
      api<{ accessToken: string; refreshToken: string; newMember: boolean }>('/api/v1/auth/kakao/login', {
        method: 'POST',
        body: { code: params.get('code'), state: params.get('state'), redirectUri: kakaoCallbackUri() }
      }).then((result) => {
        auth.save(result.accessToken, result.refreshToken);
        setSession(true);
        history.replaceState({}, '', '/');
        notify(result.newMember ? 'SOLDOUT 가입을 환영합니다.' : '로그인되었습니다.');
      }).catch((e) => notify(messageOf(e), true));
    }
    if (params.has('paymentKey') && params.has('orderId')) {
      callbackRan.current = true;
      const tossOrderId = params.get('orderId')!;
      const orderPk = Number(tossOrderId.split('_')[1]);
      api(`/api/v1/orders/${orderPk}/payments`, {
        method: 'POST', auth: true,
        body: { paymentKey: params.get('paymentKey'), tossOrderId, method: sessionStorage.getItem('payment_method') ?? 'CARD' }
      }).then(() => {
        history.replaceState({}, '', '/');
        notify('결제가 승인되었습니다.');
        go('order', orderPk);
      }).catch((e) => notify(messageOf(e), true));
    }
  }, [notify]);

  async function login() {
    try {
      const result = await api<{ url: string; state: string }>('/api/v1/auth/kakao/authorize-url');
      const url = new URL(result.url);
      url.searchParams.set('redirect_uri', kakaoCallbackUri());
      location.href = url.toString();
    } catch (e) { notify(messageOf(e), true); }
  }

  async function logout() {
    try {
      if (auth.refresh()) await api('/api/v1/auth/logout', { method: 'POST', auth: true, body: { refreshToken: auth.refresh() } });
    } catch { /* local logout always proceeds */ }
    auth.clear();
    setSession(false);
    go('shop');
    notify('로그아웃되었습니다.');
  }

  let page: ReactNode;
  if (view === 'shop') page = <Shop onOpen={(id) => go('detail', id)} />;
  else if (view === 'detail' && selectedId) page = <Detail id={selectedId} loggedIn={session} onGo={go} notify={notify} />;
  else if (view === 'cart') page = <CartPage onGo={go} notify={notify} />;
  else if (view === 'order' && selectedId) page = <OrderPage id={selectedId} onGo={go} notify={notify} />;
  else if (view === 'my') page = <MyPage loggedIn={session} onGo={go} notify={notify} />;
  else if (view === 'event') page = <EventPage notify={notify} />;
  else page = <Admin view={view} onGo={go} notify={notify} />;

  return (
    <>
      <header className="topbar">
        <button className="wordmark" onClick={() => go('shop')}>SOLDOUT<span>®</span></button>
        <nav>
          <button className={view === 'shop' ? 'active' : ''} onClick={() => go('shop')}>SHOP</button>
          <button className={view === 'event' ? 'active' : ''} onClick={() => go('event')}>DROP EVENT</button>
          <button className={view === 'cart' ? 'active' : ''} onClick={() => go('cart')}>BAG</button>
          <button className={view === 'my' ? 'active' : ''} onClick={() => go('my')}>MY</button>
          <button className={view.startsWith('admin') ? 'active' : ''} onClick={() => go('admin-products')}>ADMIN</button>
        </nav>
        <button className="login" onClick={session ? logout : login}>{session ? 'LOGOUT' : 'KAKAO LOGIN'}</button>
      </header>
      <main>{page}</main>
      <footer>
        <div className="wordmark inverse">SOLDOUT®</div>
        <p>Limited things, lasting stories.<br />실시간 재고 예약 · 안전 결제 · 정품 드랍</p>
        <small>© 2026 TEAM SOLDOUT. VERSION 3.</small>
      </footer>
      <div className="toasts">{notices.map((n) => <div key={n.id} className={n.error ? 'toast error' : 'toast'}>{n.text}</div>)}</div>
    </>
  );
}

function Shop({ onOpen }: { onOpen: (id: number) => void }) {
  const [data, setData] = useState<Page<Product>>();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState('LATEST');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(true);
    store.products(page, query, sort)
      .then(setData)
      .catch(() => setData({ content: [], page, size: 12, totalElements: 0 }))
      .finally(() => setLoading(false));
  }, [page, query, sort]);
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <span className="kicker">CURATED DROP 03 — 2026</span>
          <h1>갖고 싶은 건<br /><i>기다려주지 않으니까.</i></h1>
          <p>취향의 온도가 가장 뜨거운 순간. 한정판과 셀렉트 아이템을 가장 먼저 만나보세요.</p>
          <button className="solid" onClick={() => document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' })}>DROP 둘러보기 ↘</button>
        </div>
        <div className="hero-art">
          <div className="orbit">SOLDOUT · AUTHENTIC · LIMITED ·</div>
          <span>03</span>
        </div>
      </section>
      <div className="ticker"><div>NEW DROP&nbsp; ✦ &nbsp;AUTHENTIC ONLY&nbsp; ✦ &nbsp;LIMITED EDITION&nbsp; ✦ &nbsp;FAST CHECKOUT&nbsp; ✦ &nbsp;NEW DROP&nbsp; ✦ &nbsp;AUTHENTIC ONLY</div></div>
      <section id="catalog" className="section">
        <div className="section-head">
          <div><span className="kicker">THE EDIT</span><h2>지금, 가장 먼저.</h2></div>
          <form className="search" onSubmit={(e) => { e.preventDefault(); setPage(0); setQuery(keyword); }}>
            <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="무엇을 찾고 있나요?" />
            <button>검색</button>
          </form>
        </div>
        <div className="filters">
          {['LATEST', 'POPULAR', 'VIEW_COUNT', 'PRICE_ASC', 'PRICE_DESC'].map((value) =>
            <button key={value} className={sort === value ? 'selected' : ''} onClick={() => { setSort(value); setPage(0); }}>{({LATEST:'NEW',POPULAR:'POPULAR',VIEW_COUNT:'MOST VIEWED',PRICE_ASC:'LOW PRICE',PRICE_DESC:'HIGH PRICE'} as Record<string,string>)[value]}</button>)}
        </div>
        {loading ? <Loading /> : !data?.content.length ? <Empty text="아직 이 조건의 드랍이 없습니다." /> :
          <div className="product-grid">{data.content.map((p, i) => <ProductCard key={p.productId} product={p} index={i} onOpen={onOpen} />)}</div>}
        {data && data.totalElements > data.size && <div className="pager"><button disabled={!page} onClick={() => setPage(page - 1)}>← PREV</button><span>{String(page + 1).padStart(2,'0')} / {String(Math.ceil(data.totalElements / data.size)).padStart(2,'0')}</span><button disabled={(page + 1) * data.size >= data.totalElements} onClick={() => setPage(page + 1)}>NEXT →</button></div>}
      </section>
    </>
  );
}

function ProductCard({ product: p, index, onOpen }: { product: Product; index: number; onOpen: (id: number) => void }) {
  return <button className="product-card" onClick={() => onOpen(p.productId)}>
    <div className="product-image"><img src={imageFallback(p.productId)} alt="" /><span className="number">{String(index + 1).padStart(2,'0')}</span>{p.stocks <= 0 && <span className="sold">SOLD OUT</span>}</div>
    <div className="product-info"><div><span className="mono">DROP #{p.productId}</span><h3>{p.productName}</h3></div><div className="right"><b>{money(p.productPrice)}</b><small>{p.stocks ?? 0} LEFT</small></div></div>
  </button>;
}

function Detail({ id, loggedIn, onGo, notify }: { id: number; loggedIn: boolean; onGo: (v: View, id?: number) => void; notify: (t: string, e?: boolean) => void }) {
  const [product, setProduct] = useState<ProductDetail>();
  const [reviews, setReviews] = useState<Review[]>([]);
  const [quantity, setQuantity] = useState(1);
  const load = useCallback(() => Promise.all([store.product(id).then(setProduct), store.reviews(id).then(setReviews)]), [id]);
  useEffect(() => { load(); }, [load]);
  async function action(kind: 'cart' | 'buy') {
    if (!loggedIn) return notify('먼저 카카오 로그인이 필요합니다.', true);
    try {
      if (kind === 'cart') { await store.cartAdd(id, quantity); notify('장바구니에 담았습니다.'); }
      else { const result = await store.purchase(id, quantity); onGo('order', result.orderId); }
    } catch (e) { notify(messageOf(e), true); }
  }
  if (!product) return <Loading />;
  return <section className="section detail">
    <button className="back" onClick={() => onGo('shop')}>← ALL DROPS</button>
    <div className="detail-grid">
      <div className="gallery">
        <img className="main-photo" src={product.productImage || imageFallback(id)} alt={product.productName} />
        {product.detailImages?.map((item) => <img key={item.imageId} src={item.detailImage} alt="" />)}
      </div>
      <aside className="buy-panel">
        <span className="kicker">DROP #{id} / VERIFIED</span><h1>{product.productName}</h1><div className="detail-price">{money(product.productPrice)}</div>
        <div className="stockline"><span>AVAILABLE STOCK</span><b>{product.stocks} PCS</b></div>
        <div className="qty"><button onClick={() => setQuantity(Math.max(1, quantity - 1))}>−</button><b>{quantity}</b><button onClick={() => setQuantity(Math.min(product.stocks, quantity + 1))}>＋</button></div>
        <button className="solid wide" disabled={!product.stocks} onClick={() => action('buy')}>바로 구매하기</button>
        <button className="outline wide" disabled={!product.stocks} onClick={() => action('cart')}>장바구니에 담기</button>
        <dl><div><dt>AUTHENTICITY</dt><dd>전 상품 정품 검수</dd></div><div><dt>RESERVATION</dt><dd>주문 후 10분간 재고 예약</dd></div><div><dt>PAYMENT</dt><dd>토스페이먼츠 안전 결제</dd></div></dl>
      </aside>
    </div>
    <ReviewSection productId={id} reviews={reviews} reload={load} notify={notify} />
  </section>;
}

function ReviewSection({ productId, reviews, reload, notify }: { productId: number; reviews: Review[]; reload: () => Promise<unknown>; notify: (t: string, e?: boolean) => void }) {
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    try {
      await store.reviewCreate({ productId, rating: Number(input(form, 'rating')), content: input(form, 'content') });
      form.reset(); await reload(); notify('리뷰가 등록되었습니다.');
    } catch (err) { notify(messageOf(err), true); }
  }
  return <div className="reviews"><div className="section-head"><div><span className="kicker">COMMUNITY NOTES</span><h2>구매자 리뷰 <sup>{reviews.length}</sup></h2></div></div>
    <form className="review-form" onSubmit={submit}><select name="rating" defaultValue="5">{[5,4,3,2,1].map(n => <option key={n} value={n}>{'★'.repeat(n)} ({n})</option>)}</select><input name="content" required maxLength={500} placeholder="상품에 대한 솔직한 이야기를 남겨주세요."/><button className="solid">등록</button></form>
    <div className="review-list">{reviews.map(r => <article key={r.reviewId}><div><b>{'★'.repeat(r.rating)}{'☆'.repeat(5-r.rating)}</b><span> MEMBER #{r.memberId} · {date(r.createdAt)}</span></div><p>{r.content}</p><div><button onClick={async()=>{const content=prompt('리뷰 수정',r.content);if(content)try{await store.reviewUpdate(r.reviewId,{content,rating:r.rating});await reload();}catch(e){notify(messageOf(e),true)}}}>수정</button><button onClick={async()=>{if(confirm('리뷰를 삭제할까요?'))try{await store.reviewDelete(r.reviewId);await reload();}catch(e){notify(messageOf(e),true)}}}>삭제</button></div></article>)}</div>
  </div>;
}

function CartPage({ onGo, notify }: { onGo: (v: View, id?: number) => void; notify: (t: string, e?: boolean) => void }) {
  const [cart, setCart] = useState<Cart>();
  const load = useCallback(() => store.cart().then(setCart).catch(e => notify(messageOf(e), true)), [notify]);
  useEffect(() => { load(); }, [load]);
  if (!cart) return <Loading />;
  return <section className="section narrow"><PageTitle kicker="YOUR SELECTION" title={`Shopping Bag (${cart.totalQuantity})`} />
    {!cart.items.length ? <Empty text="장바구니가 비어 있습니다." /> : <div className="cart-layout"><div className="cart-items">{cart.items.map(item => <article key={item.cartItemId}><img src={imageFallback(item.productId)} alt=""/><div><span className="mono">DROP #{item.productId}</span><h3>{item.productName}</h3><p>{money(item.productPrice)}</p><div className="qty small"><button onClick={async()=>{if(item.quantity===1)return;await store.cartUpdate(item.cartItemId,item.quantity-1);load()}}>−</button><b>{item.quantity}</b><button onClick={async()=>{await store.cartUpdate(item.cartItemId,item.quantity+1);load()}}>＋</button></div></div><div className="right"><b>{money(item.lineTotal)}</b><button className="text-btn" onClick={async()=>{await store.cartDelete(item.cartItemId);load()}}>REMOVE</button></div></article>)}</div>
      <aside className="summary"><span className="kicker">ORDER SUMMARY</span><div><span>상품 수량</span><b>{cart.totalQuantity} PCS</b></div><div><span>배송비</span><b>FREE</b></div><div className="total"><span>TOTAL</span><b>{money(cart.totalPrice)}</b></div><button className="solid wide" onClick={async()=>{try{const r=await store.checkout();onGo('order',r.orderId)}catch(e){notify(messageOf(e),true)}}}>주문 만들기 →</button><button className="text-btn wide" onClick={async()=>{await store.cartClear();load()}}>장바구니 비우기</button></aside></div>}
  </section>;
}

function OrderPage({ id, onGo, notify }: { id: number; onGo: (v: View, id?: number) => void; notify: (t: string, e?: boolean) => void }) {
  const [order, setOrder] = useState<Order>();
  const [method, setMethod] = useState('CARD');
  const load = useCallback(() => store.order(id).then(setOrder).catch(e => notify(messageOf(e), true)), [id, notify]);
  useEffect(() => { load(); }, [load]);
  if (!order) return <Loading />;
  const currentOrder = order;
  async function pay() {
    const key = import.meta.env.VITE_TOSS_CLIENT_KEY;
    if (!key) return notify('VITE_TOSS_CLIENT_KEY를 설정해주세요.', true);
    try {
      const toss = await loadTossPayments(key);
      const tossOrderId = `ORDER_${id}_${Date.now()}`;
      sessionStorage.setItem('payment_method', method);
      await toss.payment({ customerKey: 'ANONYMOUS' }).requestPayment({
        method, amount: { currency: 'KRW', value: currentOrder.totalPrice }, orderId: tossOrderId,
        orderName: currentOrder.orderItems.length > 1 ? `${currentOrder.orderItems[0].productName} 외 ${currentOrder.orderItems.length-1}건` : currentOrder.orderItems[0]?.productName ?? `주문 ${id}`,
        successUrl: `${location.origin}/`, failUrl: `${location.origin}/`
      } as never);
    } catch(e) { notify(messageOf(e), true); }
  }
  return <section className="section narrow"><PageTitle kicker="ORDER DETAIL" title={`Order No. ${id}`} aside={<Status status={order.status}/>} />
    <div className="order-layout"><div>{order.orderItems.map(item=><article className="order-item" key={item.orderItemId}><img src={imageFallback(item.productId)} alt=""/><div><h3>{item.productName}</h3><p>{money(item.orderPrice)} × {item.quantity}</p></div><b>{money(item.itemTotalPrice)}</b></article>)}</div>
    <aside className="summary"><div><span>주문 일시</span><b>{date(order.createdAt)}</b></div>{order.paymentExpiresAt&&<div><span>결제 마감</span><b>{date(order.paymentExpiresAt)}</b></div>}<div className="total"><span>TOTAL</span><b>{money(order.totalPrice)}</b></div>
      {order.status==='PENDING_PAYMENT'&&<><div className="pay-methods">{['CARD','TRANSFER','EASY_PAY'].map(m=><button className={method===m?'selected':''} onClick={()=>setMethod(m)} key={m}>{m}</button>)}</div><button className="solid wide" onClick={pay}>결제하기 ↗</button><button className="outline wide" onClick={async()=>{try{await store.cancel(id);await load();notify('주문이 취소되었습니다.')}catch(e){notify(messageOf(e),true)}}}>주문 취소</button></>}
      {order.status==='COMPLETED'&&<button className="outline wide danger" onClick={async()=>{try{await store.refund(id);await load();notify('환불되었습니다.')}catch(e){notify(messageOf(e),true)}}}>결제 환불</button>}
      <button className="text-btn wide" onClick={()=>onGo('shop')}>계속 쇼핑하기</button></aside></div>
  </section>;
}

function MyPage({ loggedIn, onGo, notify }: { loggedIn: boolean; onGo: (v: View,id?:number)=>void; notify:(t:string,e?:boolean)=>void }) {
  const [me,setMe]=useState<{memberId:number;provider:string;email:string|null;nickname:string|null}>();
  const [orders,setOrders]=useState<Order[]>([]); const [coupons,setCoupons]=useState<MemberCoupon[]>([]);
  const [points,setPoints]=useState<{balance:number}>(); const [history,setHistory]=useState<{pointHistoryId:number;type:string;amount:number;balanceAfter:number;orderId?:number;createdAt:string}[]>([]);
  const load=useCallback(()=>{if(!loggedIn)return;Promise.all([store.me().then(setMe),store.orders().then(setOrders),store.coupons().then(setCoupons),store.points().then(setPoints),store.pointHistory().then(setHistory)]).catch(e=>notify(messageOf(e),true))},[loggedIn,notify]);
  useEffect(()=>{load()},[load]);
  if(!loggedIn)return <section className="section narrow"><Empty text="마이페이지는 로그인 후 이용할 수 있습니다."/></section>;
  if(!me)return <Loading/>;
  return <section className="section narrow"><PageTitle kicker="MEMBER ARCHIVE" title={`Hello, ${me.nickname || `Member ${me.memberId}`}`} />
    <div className="stat-grid"><div><span>POINT BALANCE</span><b>{money(points?.balance??0)}</b></div><div><span>MY ORDERS</span><b>{orders.length}</b></div><div><span>USABLE COUPONS</span><b>{coupons.filter(c=>c.usable).length}</b></div></div>
    <div className="my-grid"><section className="panel"><h3>PROFILE</h3><form onSubmit={async e=>{e.preventDefault();try{await store.updateMe({nickname:input(e.currentTarget,'nickname'),email:input(e.currentTarget,'email')});await load();notify('프로필을 저장했습니다.')}catch(err){notify(messageOf(err),true)}}}><label>닉네임<input name="nickname" defaultValue={me.nickname??''}/></label><label>이메일<input type="email" name="email" defaultValue={me.email??''}/></label><button className="solid">SAVE PROFILE</button></form></section>
    <section className="panel"><h3>ISSUE COUPON</h3><QuickId label="쿠폰 ID" button="쿠폰 받기" onSubmit={async id=>{try{await store.issueCoupon(id);await load();notify('쿠폰을 발급받았습니다.')}catch(e){notify(messageOf(e),true)}}}/></section></div>
    <DataSection title="ORDERS">{orders.length?orders.map(o=><button className="list-row" key={o.orderId} onClick={()=>onGo('order',o.orderId)}><span>#{o.orderId} · {date(o.createdAt)}</span><Status status={o.status}/><b>{money(o.totalPrice)} →</b></button>):<p className="muted">주문 내역이 없습니다.</p>}</DataSection>
    <DataSection title="COUPONS">{coupons.length?coupons.map(c=><div className="coupon" key={c.memberCouponId}><div><span>{c.usable?'AVAILABLE':'USED / EXPIRED'}</span><h3>{c.coupon.couponName}</h3><p>{c.coupon.discountType==='RATE'?`${c.coupon.discountValue}%`:`${money(c.coupon.discountValue)}`} 할인 · {money(c.coupon.minOrderAmount)} 이상</p></div><b>{date(c.expiresAt)}</b></div>):<p className="muted">보유 쿠폰이 없습니다.</p>}</DataSection>
    <DataSection title="POINT HISTORY">{history.length?history.map(h=><div className="list-row" key={h.pointHistoryId}><span>{date(h.createdAt)} · {h.type}{h.orderId?` · ORDER #${h.orderId}`:''}</span><b>{h.type==='USE'?'-':'+'}{money(h.amount)} <small>/ {money(h.balanceAfter)}</small></b></div>):<p className="muted">포인트 내역이 없습니다.</p>}</DataSection>
  </section>;
}

function EventPage({notify}:{notify:(t:string,e?:boolean)=>void}) {
  return <section className="event-page"><div><span className="kicker light">FIRST COME, FIRST SERVED</span><h1>THE<br/>ONE<br/>CHANCE.</h1><p>단 한 번의 클릭, 한정된 기회.<br/>이벤트 ID를 입력하고 가장 먼저 참여하세요.</p><QuickId label="이벤트 ID" button="지금 참여하기 →" onSubmit={async id=>{try{const r=await store.event(id);notify(`참여 성공! 남은 수량 ${r.remainingCount}개`)}catch(e){notify(messageOf(e),true)}}} light/></div><div className="event-number">01</div></section>;
}

function Admin({view,onGo,notify}:{view:View;onGo:(v:View)=>void;notify:(t:string,e?:boolean)=>void}) {
  const tabs:[View,string][]=[['admin-products','PRODUCTS'],['admin-categories','CATEGORIES'],['admin-stock','STOCK'],['admin-coupons','COUPONS']];
  return <section className="section admin"><PageTitle kicker="OPERATIONS" title="Admin Studio"/><AdminAccess notify={notify}/><div className="admin-tabs">{tabs.map(([v,l])=><button className={view===v?'active':''} key={v} onClick={()=>onGo(v)}>{l}</button>)}</div>
    {view==='admin-products'?<AdminProducts notify={notify}/>:view==='admin-categories'?<AdminCategories notify={notify}/>:view==='admin-stock'?<AdminStock notify={notify}/>:<AdminCoupons notify={notify}/>}
  </section>;
}

function tokenRole(token: string | null): string {
  if (!token) return 'NO TOKEN';
  try {
    const payload = token.split('.')[1].replaceAll('-', '+').replaceAll('_', '/');
    return String(JSON.parse(decodeURIComponent(escape(atob(payload)))).role ?? 'UNKNOWN');
  } catch { return 'INVALID TOKEN'; }
}

function AdminAccess({notify}:{notify:(t:string,e?:boolean)=>void}) {
  const [role,setRole]=useState(()=>tokenRole(auth.access()));
  const [server,setServer]=useState('CHECKING');
  const check=useCallback(()=>store.categories().then(()=>setServer('CONNECTED')).catch(()=>setServer('OFFLINE')),[]);
  useEffect(()=>{check()},[check]);
  return <div className="access-panel">
    <div><span>BACKEND</span><b className={server==='CONNECTED'?'ok':'warn'}>{server}</b></div>
    <div><span>ACCESS ROLE</span><b className={role==='ADMIN'?'ok':'warn'}>{role}</b></div>
    <form onSubmit={e=>{e.preventDefault();const token=input(e.currentTarget,'token');auth.setAccess(token.replace(/^Bearer\\s+/i,''));const next=tokenRole(auth.access());setRole(next);notify(next==='ADMIN'?'ADMIN 토큰이 적용되었습니다.':'토큰은 적용됐지만 ADMIN 권한이 아닙니다.',next!=='ADMIN')}}>
      <input name="token" placeholder="테스트용 ADMIN access token 붙여넣기"/>
      <button>APPLY TOKEN</button>
    </form>
    <p>상품·카테고리 변경은 백엔드 정책상 ADMIN 토큰이 필요합니다. 일반 카카오 계정은 USER로 생성됩니다.</p>
  </div>;
}

function AdminProducts({notify}:{notify:(t:string,e?:boolean)=>void}) {
  const [products,setProducts]=useState<Product[]>([]);const [categories,setCategories]=useState<Category[]>([]);
  const load=useCallback(async()=>{const p=await store.products(0,'','LATEST');setProducts(p.content);const parents=await store.categories();const details=await Promise.all(parents.map(p=>store.category(p.categoryId).catch(()=>({children:[]}))));setCategories([...parents,...details.flatMap((d,i)=>d.children.filter(c=>c.categoryId&&c.categoryName).map(c=>({categoryId:c.categoryId!,categoryName:c.categoryName!,parentCategory:parents[i].categoryId})))])},[]);
  useEffect(()=>{load().catch(e=>notify(messageOf(e),true))},[load,notify]);
  async function create(e:FormEvent<HTMLFormElement>){e.preventDefault();const form=e.currentTarget;const file=(new FormData(form).get('image')) as File;if(!file.size)return notify('대표 이미지를 선택해주세요.',true);const request={productName:input(form,'name'),productPrice:Number(input(form,'price')),stock:Number(input(form,'stock')),category:Number(input(form,'category'))};try{await store.productCreate(request,file);form.reset();await load();notify('상품을 등록했습니다.')}catch(err){notify(messageOf(err),true)}}
  return <><form className="admin-form" onSubmit={create}><h3>NEW PRODUCT</h3><input name="name" required placeholder="상품명"/><input name="price" required min="1" type="number" placeholder="가격"/><input name="stock" required min="1" type="number" placeholder="초기 재고"/><select name="category" required><option value="">중분류 선택</option>{categories.filter(c=>c.parentCategory!==null).map(c=><option value={c.categoryId} key={c.categoryId}>{c.categoryName}</option>)}</select><label className="file">대표 이미지<input name="image" required type="file" accept="image/*"/></label><button className="solid">REGISTER</button></form>
  <div className="admin-list">{products.map(p=><article key={p.productId}><span>#{p.productId}</span><h3>{p.productName}</h3><b>{money(p.productPrice)}</b><small>{p.stocks} PCS</small><button onClick={async()=>{const name=prompt('새 상품명',p.productName);const price=prompt('새 가격',String(p.productPrice));const category=prompt('중분류 ID');if(name&&price&&category)try{await store.productUpdate(p.productId,{productName:name,productPrice:Number(price),category:Number(category)});await load();notify('수정했습니다.')}catch(e){notify(messageOf(e),true)}}}>EDIT</button><button onClick={async()=>{if(confirm('상품을 삭제할까요?'))try{await store.productDelete(p.productId);await load()}catch(e){notify(messageOf(e),true)}}}>DELETE</button><label className="mini-file">+ DETAIL<input type="file" accept="image/*" onChange={async e=>{const f=e.target.files?.[0];if(f)try{await store.imageAdd(p.productId,f);notify('상세 이미지를 추가했습니다.')}catch(err){notify(messageOf(err),true)}}}/></label></article>)}</div></>;
}

function AdminCategories({notify}:{notify:(t:string,e?:boolean)=>void}) {
  const [items,setItems]=useState<Category[]>([]);
  const load=useCallback(async()=>{const parents=await store.categories();const details=await Promise.all(parents.map(p=>store.category(p.categoryId).catch(()=>({children:[]}))));setItems([...parents,...details.flatMap((d,i)=>d.children.filter(c=>c.categoryId&&c.categoryName).map(c=>({categoryId:c.categoryId!,categoryName:c.categoryName!,parentCategory:parents[i].categoryId})))])},[]);
  useEffect(()=>{load().catch(e=>notify(messageOf(e),true))},[load,notify]);
  return <><form className="admin-form compact" onSubmit={async e=>{e.preventDefault();const form=e.currentTarget;const name=input(form,'name');try{await store.categoryCreate(name);form.reset();await load()}catch(err){notify(messageOf(err),true)}}}><h3>NEW ROOT CATEGORY</h3><input required name="name" placeholder="대분류 이름"/><button className="solid">CREATE</button></form>
  <div className="category-groups">{items.filter(c=>c.parentCategory===null).map(parent=><section key={parent.categoryId}><article className="category-root"><span>ROOT #{parent.categoryId}</span><h3>{parent.categoryName}</h3><div><button onClick={async()=>{const n=prompt('새 이름',parent.categoryName);if(n)try{await store.categoryUpdate(parent.categoryId,n);await load()}catch(e){notify(messageOf(e),true)}}}>RENAME</button><button onClick={async()=>{const n=prompt('추가할 중분류 이름');if(n)try{await store.categoryChild(parent.categoryId,n);await load();notify('중분류를 추가했습니다.')}catch(e){notify(messageOf(e),true)}}}>+ CHILD</button><button onClick={async()=>{if(confirm('대분류를 삭제할까요?'))try{await store.categoryDelete(parent.categoryId);await load()}catch(e){notify(messageOf(e),true)}}}>DELETE</button></div></article>
    <div className="category-children">{items.filter(c=>c.parentCategory===parent.categoryId).map(child=><article key={child.categoryId}><span>CHILD #{child.categoryId}</span><h4>{child.categoryName}</h4><div><button onClick={async()=>{const n=prompt('새 이름',child.categoryName);if(n)try{await store.categoryUpdate(child.categoryId,n);await load()}catch(e){notify(messageOf(e),true)}}}>RENAME</button><button onClick={async()=>{if(confirm('중분류를 삭제할까요?'))try{await store.categoryDelete(child.categoryId);await load()}catch(e){notify(messageOf(e),true)}}}>DELETE</button></div></article>)}</div></section>)}</div></>;
}

function AdminStock({notify}:{notify:(t:string,e?:boolean)=>void}) {
  const [products,setProducts]=useState<Product[]>([]);const [id,setId]=useState(0);const [history,setHistory]=useState<{historyId:number;type:string;changedQty:number;currentStocks:number;reason?:string;createdAt:string}[]>([]);
  useEffect(()=>{store.products(0,'','LATEST').then(r=>setProducts(r.content))},[]);
  const load=useCallback(()=>id?store.histories(id).then(setHistory):Promise.resolve(),[id]);useEffect(()=>{load()},[load]);
  return <><form className="admin-form compact" onSubmit={async e=>{e.preventDefault();try{await store.stockIn(id,Number(input(e.currentTarget,'quantity')),input(e.currentTarget,'reason'));await load();notify('입고를 반영했습니다.')}catch(err){notify(messageOf(err),true)}}}><h3>STOCK IN</h3><select required value={id||''} onChange={e=>setId(Number(e.target.value))}><option value="">상품 선택</option>{products.map(p=><option value={p.productId} key={p.productId}>#{p.productId} {p.productName} ({p.stocks})</option>)}</select><input name="quantity" required min="1" type="number" placeholder="수량"/><input name="reason" required defaultValue="관리자 입고" placeholder="사유"/><button className="solid">APPLY</button></form>
  <DataSection title="STOCK HISTORY">{history.map(h=><div className="list-row" key={h.historyId}><span>{date(h.createdAt)} · {h.type} · {h.reason||'-'}</span><b>{h.changedQty>0?'+':''}{h.changedQty} <small>/ {h.currentStocks} PCS</small></b></div>)}</DataSection></>;
}

function AdminCoupons({notify}:{notify:(t:string,e?:boolean)=>void}) {
  async function create(e:FormEvent<HTMLFormElement>){e.preventDefault();const form=e.currentTarget;const rate=input(form,'type')==='RATE';const request={couponName:input(form,'name'),discountType:input(form,'type'),discountValue:Number(input(form,'value')),maxDiscountAmount:rate?Number(input(form,'max')):null,minOrderAmount:Number(input(form,'min')),totalQuantity:Number(input(form,'quantity')),issueStartAt:input(form,'start'),issueEndAt:input(form,'end'),validDays:Number(input(form,'days'))};try{await store.couponCreate(request);form.reset();notify('쿠폰을 생성했습니다.')}catch(err){notify(messageOf(err),true)}}
  return <form className="admin-form coupon-form" onSubmit={create}><h3>NEW COUPON POLICY</h3><input name="name" required placeholder="쿠폰명"/><select name="type"><option value="FIXED">정액 할인</option><option value="RATE">정률 할인</option></select><input name="value" required min="1" type="number" placeholder="할인 금액 / 할인율"/><input name="max" min="1" type="number" placeholder="최대 할인액 (정률)"/><input name="min" required min="0" type="number" placeholder="최소 주문액"/><input name="quantity" required min="1" type="number" placeholder="발급 수량"/><label>발급 시작<input name="start" required type="datetime-local"/></label><label>발급 종료<input name="end" required type="datetime-local"/></label><input name="days" required min="1" type="number" placeholder="사용 가능 일수"/><button className="solid">CREATE COUPON</button></form>;
}

function PageTitle({kicker,title,aside}:{kicker:string;title:string;aside?:ReactNode}){return <header className="page-title"><div><span className="kicker">{kicker}</span><h1>{title}</h1></div>{aside}</header>}
function Loading(){return <div className="loading"><span></span><p>ARCHIVING...</p></div>}
function Empty({text}:{text:string}){return <div className="empty"><b>∅</b><p>{text}</p></div>}
function Status({status}:{status:string}){return <span className={`status ${status.toLowerCase()}`}>{status.replaceAll('_',' ')}</span>}
function DataSection({title,children}:{title:string;children:ReactNode}){return <section className="data-section"><h3>{title}</h3>{children}</section>}
function QuickId({label,button,onSubmit,light=false}:{label:string;button:string;onSubmit:(id:number)=>void;light?:boolean}){return <form className={`quick-id ${light?'light':''}`} onSubmit={e=>{e.preventDefault();onSubmit(Number(input(e.currentTarget,'id')))}}><input required min="1" type="number" name="id" placeholder={label}/><button>{button}</button></form>}
function messageOf(error:unknown){return error instanceof ApiError?error.message:error instanceof Error?error.message:'요청을 처리하지 못했습니다.'}
