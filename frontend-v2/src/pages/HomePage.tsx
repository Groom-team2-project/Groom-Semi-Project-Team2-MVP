import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getProducts } from '../api/products';
import { ProductCard, ProductCardSkeleton, formatPrice, photoOf } from '../components/ProductCard';

const MARQUEE_ITEMS = ['Just Dropped', 'Limited Edition', 'Sold Out Soon', 'Verified Authentic'];

export function HomePage() {
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['products', page, search],
    queryFn: () => getProducts({ page, size: 12, keyword: search || undefined })
  });

  const totalPages = data ? Math.max(1, Math.ceil(data.totalElements / data.size)) : 1;
  const totalCount = data?.totalElements ?? 0;
  const featured = !search && page === 0 ? data?.content[0] : undefined;

  return (
    <>
      {/* 메인 배너 — 대표 드랍 실사 */}
      <section className="banner rise">
        <img
          className="bg"
          src={featured ? photoOf(featured.productId, 1200) : 'https://picsum.photos/seed/soldout-hero/1200/700'}
          alt=""
        />
        <div className="banner-copy">
          <span className="eyebrow">This Week's Drop</span>
          <h1 className="h-display" style={{ fontSize: 'clamp(28px,4.2vw,46px)' }}>
            {featured ? featured.productName : '갖고 싶던 그 순간, SOLDOUT 되기 전에.'}
          </h1>
          <p>
            {featured
              ? `즉시 구매가 ${formatPrice(featured.productPrice)} — 재고 소진 시 드랍이 종료됩니다.`
              : '한정판 드랍 커머스. 실시간 재고 예약으로 결제 순간까지 안전하게.'}
          </p>
          {featured && (
            <Link to={`/products/${featured.productId}`} className="btn btn-buy" style={{ marginTop: 18 }}>
              드랍 보러가기 <span className="chip">→</span>
            </Link>
          )}
        </div>
      </section>

      {/* 키네틱 마퀴 */}
      <div className="marquee" aria-hidden>
        <div className="marquee-track">
          {[0, 1].map((half) => (
            <span key={half}>
              {MARQUEE_ITEMS.map((item) => (
                <span key={item}>
                  {item} <span className="dot">●</span>{' '}
                </span>
              ))}
            </span>
          ))}
        </div>
      </div>

      {/* 검색 + 섹션 헤더 */}
      <div className="row between" style={{ marginBottom: 20, flexWrap: 'wrap', gap: 14 }}>
        <div>
          <h2 className="h-section">{search ? `"${search}" 검색 결과` : 'New Drops'}</h2>
          <span className="text-muted" style={{ fontSize: 13 }}>
            {isLoading ? '불러오는 중' : `${totalCount}개의 드랍`}
          </span>
        </div>
        <form
          className="row"
          onSubmit={(e) => {
            e.preventDefault();
            setPage(0);
            setSearch(keyword.trim());
          }}
        >
          <input
            className="input"
            style={{ borderRadius: 999, width: 240, paddingLeft: 18 }}
            placeholder="드랍 검색"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <button className="btn btn-primary btn-sm" type="submit">검색</button>
        </form>
      </div>

      {isLoading ? (
        <ProductCardSkeleton />
      ) : !data || data.content.length === 0 ? (
        <div className="empty rise">
          <div className="empty-mark">S</div>
          {search ? <>"{search}" 검색 결과가 없어요.</> : <>아직 등록된 드랍이 없어요. Admin에서 첫 상품을 등록해보세요.</>}
        </div>
      ) : (
        <>
          <div className="grid-products">
            {data.content.map((p, i) => (
              <ProductCard key={p.productId} product={p} index={i} />
            ))}
          </div>
          {totalPages > 1 && (
            <div className="row" style={{ justifyContent: 'center', marginTop: 44 }}>
              <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
                이전
              </button>
              <span className="text-muted" style={{ fontVariantNumeric: 'tabular-nums' }}>
                {page + 1} / {totalPages}
              </span>
              <button
                className="btn btn-ghost btn-sm"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage(page + 1)}
              >
                다음
              </button>
            </div>
          )}
        </>
      )}

      {/* 신뢰 배너 — 쇼핑몰다운 마감 */}
      <section
        className="row"
        style={{ marginTop: 72, gap: 0, borderTop: '1px solid var(--hairline)', paddingTop: 36, flexWrap: 'wrap' }}
      >
        {[
          ['정품 검수', '모든 드랍은 검수 후 배송돼요'],
          ['재고 예약', '주문 즉시 재고가 안전하게 예약돼요'],
          ['간편 환불', '결제 완료 후에도 원클릭 환불']
        ].map(([title, desc]) => (
          <div key={title} style={{ flex: '1 1 200px', padding: '0 20px 20px 0' }}>
            <b style={{ fontSize: 14 }}>{title}</b>
            <p className="text-muted" style={{ fontSize: 13, marginTop: 4 }}>{desc}</p>
          </div>
        ))}
      </section>
    </>
  );
}
