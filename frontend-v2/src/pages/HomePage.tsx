import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getProducts } from '../api/products';
import { ProductCard } from '../components/ProductCard';

export function HomePage() {
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['products', page, search],
    queryFn: () => getProducts({ page, size: 12, keyword: search || undefined })
  });

  const totalPages = data ? Math.max(1, Math.ceil(data.totalElements / data.size)) : 1;

  return (
    <>
      <header style={{ marginBottom: 48 }} className="rise">
        <span className="eyebrow">Limited Drop Commerce</span>
        <h1 className="h-display">
          갖고 싶던 그 순간,
          <br />
          SOLDOUT 되기 전에.
        </h1>
        <form
          className="row"
          style={{ marginTop: 28, maxWidth: 440 }}
          onSubmit={(e) => {
            e.preventDefault();
            setPage(0);
            setSearch(keyword.trim());
          }}
        >
          <input
            className="input"
            style={{ flex: 1, borderRadius: 999 }}
            placeholder="상품명 검색"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <button className="btn btn-primary btn-sm" type="submit">
            검색
          </button>
        </form>
      </header>

      {isLoading ? (
        <div className="spin" />
      ) : !data || data.content.length === 0 ? (
        <div className="empty">
          {search ? `"${search}" 검색 결과가 없어요.` : '아직 등록된 상품이 없어요. Admin에서 상품을 등록해보세요.'}
        </div>
      ) : (
        <>
          <div className="grid-products">
            {data.content.map((p, i) => (
              <ProductCard key={p.productId} product={p} index={i} />
            ))}
          </div>
          {totalPages > 1 && (
            <div className="row" style={{ justifyContent: 'center', marginTop: 40 }}>
              <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
                이전
              </button>
              <span className="text-muted">
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
    </>
  );
}
