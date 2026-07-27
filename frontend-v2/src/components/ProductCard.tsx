import { Link } from 'react-router-dom';
import type { ProductListItem } from '../api/types';

export function formatPrice(price: number) {
  return `${price.toLocaleString()}원`;
}

// 이미지 API가 목록에 없어 상품명 이니셜을 비주얼로 사용 (KREAM식 모노 카드)
export function initialOf(name: string) {
  return name.trim().slice(0, 2).toUpperCase();
}

export function ProductCard({ product, index }: { product: ProductListItem; index: number }) {
  return (
    <Link
      to={`/products/${product.productId}`}
      className={`card-product rise rise-${(index % 3) + 1}`}
    >
      <div className="card-visual">{initialOf(product.productName)}</div>
      <div className="card-body">
        <div className="name">{product.productName}</div>
        <div className="sub">SOLDOUT DROP</div>
        <div className="price buy">{formatPrice(product.productPrice)}</div>
      </div>
    </Link>
  );
}
