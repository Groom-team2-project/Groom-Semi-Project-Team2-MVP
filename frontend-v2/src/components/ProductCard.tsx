import { Link } from 'react-router-dom';
import type { ProductListItem } from '../api/types';

export function formatPrice(price: number) {
  return `${price.toLocaleString()}원`;
}

// 상품 이미지 API가 없어 상품 ID 시드 기반 플레이스홀더 사진 사용
// (같은 상품 = 항상 같은 사진, 실제 이미지 연동 시 이 함수만 교체하면 됨)
export function photoOf(productId: number, size = 600) {
  return `https://picsum.photos/seed/soldout-${productId}/${size}/${size}`;
}

export function ProductCard({ product, index }: { product: ProductListItem; index: number }) {
  return (
    <Link
      to={`/products/${product.productId}`}
      className={`card-product rise rise-${(index % 3) + 1}`}
    >
      <div className="card-photo">
        <img src={photoOf(product.productId)} alt={product.productName} loading="lazy" />
        <span className="tag">Drop #{product.productId}</span>
      </div>
      <div className="card-body">
        <div className="brand-line">SOLDOUT</div>
        <div className="name">{product.productName}</div>
        <div className="price">{formatPrice(product.productPrice)}</div>
        <div className="price-label">즉시 구매가</div>
      </div>
    </Link>
  );
}

export function ProductCardSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="grid-products">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="sk sk-card" />
      ))}
    </div>
  );
}
