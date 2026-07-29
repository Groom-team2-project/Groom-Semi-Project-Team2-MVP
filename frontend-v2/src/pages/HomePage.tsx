import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getProducts } from '../api/products';
import { getCategoryTree } from '../api/categories';
import type { CategoryResponse, ProductSortType } from '../api/types';
import { ProductCard, ProductCardSkeleton, formatPrice, photoOf } from '../components/ProductCard';

const MARQUEE_ITEMS = ['Just Dropped', 'Limited Edition', 'Sold Out Soon', 'Verified Authentic'];

const SORT_OPTIONS: { value: ProductSortType; label: string }[] = [
    { value: 'POPULAR', label: '인기도순' },
    { value: 'LATEST', label: '최신순' },
    { value: 'VIEW_COUNT', label: '조회수순' },
    { value: 'PRICE_ASC', label: '낮은 가격순' },
    { value: 'PRICE_DESC', label: '높은 가격순' }
];

export function HomePage() {
    const [keyword, setKeyword] = useState('');
    const [search, setSearch] = useState('');
    const [page, setPage] = useState(0);
    const [parentCategoryId, setParentCategoryId] = useState<number | undefined>(undefined);
    const [categoryId, setCategoryId] = useState<number | undefined>(undefined);
    const [sort, setSort] = useState<ProductSortType>('POPULAR');

    const { data: categories } = useQuery({
        queryKey: ['categoryTree'],
        queryFn: getCategoryTree,
        staleTime: 5 * 60 * 1000
    });

    const parentCategories = categories?.filter((category) => category.parentCategory == null) ?? [];
    const childCategoriesByParent = new Map<number, CategoryResponse[]>();

    categories
        ?.filter((category) => category.parentCategory != null)
        .forEach((category) => {
            const parentId = category.parentCategory as number;
            const children = childCategoriesByParent.get(parentId) ?? [];
            children.push(category);
            childCategoriesByParent.set(parentId, children);
        });

    const childCategories = parentCategoryId
        ? childCategoriesByParent.get(parentCategoryId) ?? []
        : [];

    const appliedCategoryId = categoryId ?? parentCategoryId;

    const selectedParentName = parentCategoryId
        ? parentCategories.find(
            (category) => category.categoryId === parentCategoryId
        )?.categoryName
        : undefined;

    const selectedChildName = categoryId
        ? childCategories.find(
            (category) => category.categoryId === categoryId
        )?.categoryName
        : undefined;

    const { data, isLoading } = useQuery({
        queryKey: ['products', page, search, appliedCategoryId, sort],
        queryFn: () =>
            getProducts({
                page,
                size: 12,
                keyword: search || undefined,
                categoryId: appliedCategoryId,
                sort
            })
    });

    const totalPages = data ? Math.max(1, Math.ceil(data.totalElements / data.size)) : 1;
    const totalCount = data?.totalElements ?? 0;
    const featured =
        !search && !appliedCategoryId && page === 0
            ? data?.content[0]
            : undefined;

    const catalogTitle = search
        ? `"${search}" 검색 결과`
        : selectedChildName ?? selectedParentName ?? '전체상품';

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
                    <span className="eyebrow">This Week&apos;s Drop</span>
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

            {/* 상품 검색 + 카테고리 + 정렬 */}
            <section className="catalog-toolbar">
                <div className="catalog-heading">
                    <div>
                        <h2 className="h-section">{catalogTitle}</h2>

                        <span className="text-muted catalog-count">
                {isLoading ? '불러오는 중' : `${totalCount}개의 상품`}
            </span>
                    </div>

                    <form
                        className="catalog-search"
                        onSubmit={(event) => {
                            event.preventDefault();
                            setPage(0);
                            setSearch(keyword.trim());
                        }}
                    >
                        <input
                            className="input catalog-search-input"
                            placeholder="상품명을 검색하세요"
                            value={keyword}
                            onChange={(event) => setKeyword(event.target.value)}
                        />

                        <button className="btn btn-primary btn-sm" type="submit">
                            검색
                        </button>

                        <button
                            type="button"
                            className="catalog-search-reset"
                            disabled={
                                !keyword &&
                                !search &&
                                parentCategoryId == null &&
                                categoryId == null &&
                                sort === 'POPULAR'
                            }
                            onClick={() => {
                                setKeyword('');
                                setSearch('');
                                setParentCategoryId(undefined);
                                setCategoryId(undefined);
                                setSort('POPULAR');
                                setPage(0);
                            }}
                        >
                            초기화
                        </button>
                    </form>
                </div>

                <div className="catalog-control-panel">
                    <div className="catalog-category-area">

        <div className="category-filter-row">
            <span className="category-filter-label">대분류</span>

            <div className="category-pill-list">
                <button
                    type="button"
                    className={`category-pill ${
                        parentCategoryId == null ? 'active' : ''
                    }`}
                    onClick={() => {
                        setPage(0);
                        setParentCategoryId(undefined);
                        setCategoryId(undefined);
                    }}
                >
                    전체
                </button>

                {parentCategories.map((parent) => (
                    <button
                        type="button"
                        key={parent.categoryId}
                        className={`category-pill ${
                            parentCategoryId === parent.categoryId
                                ? 'active'
                                : ''
                        }`}
                        onClick={() => {
                            setPage(0);
                            setParentCategoryId(parent.categoryId);
                            setCategoryId(undefined);
                        }}
                    >
                        {parent.categoryName}
                    </button>
                ))}
            </div>
        </div>
            <div className="category-filter-row category-filter-row-child">
                <span className="category-filter-label">중분류</span>

                <div className="category-pill-list">
                    <button
                        type="button"
                        className={`category-pill category-pill-sub ${
                            categoryId == null ? 'active' : ''
                        }`}
                        onClick={() => {
                            setPage(0);
                            setCategoryId(undefined);
                        }}
                    >
                        전체
                    </button>

                    {parentCategoryId != null &&
                        childCategories.map((child) => (
                            <button
                                type="button"
                                key={child.categoryId}
                                className={`category-pill category-pill-sub ${
                                    categoryId === child.categoryId
                                        ? 'active'
                                        : ''
                                }`}
                                onClick={() => {
                                    setPage(0);
                                    setCategoryId(child.categoryId);
                                }}
                            >
                                {child.categoryName}
                            </button>
                        ))}
                </div>
            </div>


        </div>

        <div
            className="sort-filter"
            role="group"
            aria-label="상품 정렬"
        >
            {SORT_OPTIONS.map((option, index) => (
                <span
                    className="sort-filter-item"
                    key={option.value}
                >
                    {index > 0 && (
                        <span
                            className="sort-separator"
                            aria-hidden
                        >
                            |
                        </span>
                    )}

                    <button
                        type="button"
                        className={`sort-option ${
                            sort === option.value ? 'active' : ''
                        }`}
                        aria-pressed={sort === option.value}
                        onClick={() => {
                            setPage(0);
                            setSort(option.value);
                        }}
                    >
                        {sort === option.value && (
                            <span aria-hidden>✓</span>
                        )}

                        {option.label}
                    </button>
                </span>
            ))}
        </div>
    </div>
</section>

            {isLoading ? (
                <ProductCardSkeleton />
            ) : !data || data.content.length === 0 ? (
                <div className="empty rise">
                    <div className="empty-mark">S</div>
                    {search || appliedCategoryId ? (
                        <>
                            {search && `"${search}" `}검색 결과가 없어요.
                        </>
                    ) : (
                        <>아직 등록된 드랍이 없어요. Admin에서 첫 상품을 등록해보세요.</>
                    )}
                </div>
            ) : (
                <>
                    <div className="grid-products">
                        {data.content.map((product, index) => (
                            <ProductCard key={product.productId} product={product} index={index} />
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
                ].map(([title, description]) => (
                    <div key={title} style={{ flex: '1 1 200px', padding: '0 20px 20px 0' }}>
                        <b style={{ fontSize: 14 }}>{title}</b>
                        <p className="text-muted" style={{ fontSize: 13, marginTop: 4 }}>
                            {description}
                        </p>
                    </div>
                ))}
            </section>
        </>
    );
}
