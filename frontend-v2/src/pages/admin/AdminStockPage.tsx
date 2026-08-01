import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getAllProducts } from '../../api/products';
import { stockIn, getStockHistories } from '../../api/stock';
import { ApiError } from '../../api/client';
import { useToast } from '../../components/Toast';
import { AdminShell } from './AdminLayout';

const TYPE_LABEL: Record<string, string> = {
  INBOUND: '입고',
  DECREASE: '차감',
  RESTORE: '복구',
  RESERVE: '예약',
  CONFIRM: '확정',
  RELEASE: '해제'
};

export function AdminStockPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [productId, setProductId] = useState('');
  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState('관리자 입고');

  const { data: products } = useQuery({
    queryKey: ['admin-products-all'],
    queryFn: getAllProducts
  });

  const { data: histories, isLoading: historiesLoading, isError: isHistoriesError, error: historiesError } = useQuery({
    queryKey: ['stock-histories', productId],
    queryFn: () => getStockHistories(productId),
    enabled: Boolean(productId)
  });

  const stockInMutation = useMutation({
    mutationFn: () => stockIn(productId, Number(quantity), reason.trim() || '관리자 입고'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock-histories', productId] });
      queryClient.invalidateQueries({ queryKey: ['admin-products-all'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['product', productId] });
      setQuantity('');
      toast('입고 처리되었어요.');
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : '입고에 실패했어요.', 'error')
  });

  return (
    <AdminShell title="재고 관리">
      <div className="bezel rise rise-1" style={{ marginBottom: 24 }}>
        <div className="core" style={{ padding: 8, overflowX: 'auto' }}>
          <div className="row between" style={{ padding: '10px 10px 14px' }}>
            <div>
              <h2 className="h-section">전체 재고 현황</h2>
              <p className="text-muted" style={{ marginTop: 4, fontSize: 13 }}>
                결제 대기 주문의 예약분을 제외한 구매 가능 재고를 함께 확인할 수 있어요.
              </p>
            </div>
            <button className="btn btn-ghost btn-sm" onClick={() => queryClient.invalidateQueries({ queryKey: ['admin-products-all'] })}>
              새로고침
            </button>
          </div>
          <table className="table">
            <thead>
              <tr><th>상품</th><th>실제 재고</th><th>예약 재고</th><th>구매 가능</th><th /></tr>
            </thead>
            <tbody>
              {(products ?? []).length === 0 && (
                <tr><td colSpan={5} className="text-muted" style={{ textAlign: 'center' }}>등록된 상품이 없어요.</td></tr>
              )}
              {(products ?? []).map((product) => (
                <tr key={product.productId}>
                  <td><b>#{product.productId} {product.productName}</b></td>
                  <td>{product.stocks}개</td>
                  <td>{product.reservedStocks}개</td>
                  <td><b>{product.availableStocks}개</b></td>
                  <td>
                    <button className="btn btn-ghost btn-sm" onClick={() => setProductId(String(product.productId))}>
                      상세 보기
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="bezel rise rise-1" style={{ marginBottom: 24 }}>
        <div className="core">
          <h2 className="h-section" style={{ marginBottom: 14 }}>입고 처리</h2>
          <form
            className="row"
            style={{ flexWrap: 'wrap' }}
            onSubmit={(e) => {
              e.preventDefault();
              if (!productId) return toast('상품을 선택해주세요.', 'error');
              if (!quantity || Number(quantity) <= 0) return toast('입고 수량을 입력해주세요.', 'error');
              stockInMutation.mutate();
            }}
          >
            <select className="input" value={productId} onChange={(e) => setProductId(e.target.value)} style={{ flex: 2, minWidth: 180 }}>
              <option value="">상품 선택</option>
              {(products ?? []).map((p) => (
                <option key={p.productId} value={p.productId}>#{p.productId} {p.productName}</option>
              ))}
            </select>
            <input className="input" placeholder="수량" type="number" min={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} style={{ flex: 1, minWidth: 100 }} />
            <input className="input" placeholder="사유" value={reason} onChange={(e) => setReason(e.target.value)} style={{ flex: 1, minWidth: 130 }} />
            <button className="btn btn-primary btn-sm" disabled={stockInMutation.isPending}>입고</button>
          </form>
        </div>
      </div>

      {!productId ? (
        <div className="empty">상품을 선택하면 재고 변동 이력이 보여요.</div>
      ) : historiesLoading ? (
        <div className="spin" />
      ) : isHistoriesError ? (
        <div className="empty">
          재고 이력을 불러오지 못했어요. {historiesError instanceof ApiError ? historiesError.message : '관리자 권한을 다시 확인해주세요.'}
        </div>
      ) : (
        <div className="bezel rise rise-2">
          <div className="core" style={{ padding: 8, overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr><th>시각</th><th>유형</th><th>변동</th><th>현재고</th><th>주문</th><th>사유</th></tr>
              </thead>
              <tbody>
                {(histories ?? []).length === 0 && (
                  <tr><td colSpan={6} className="text-muted" style={{ textAlign: 'center' }}>재고 변동 이력이 없어요. 최초 등록 재고는 위 현황에서 확인할 수 있어요.</td></tr>
                )}
                {(histories ?? []).map((h) => (
                  <tr key={h.historyId}>
                    <td className="text-muted" style={{ fontSize: 12 }}>{new Date(h.createdAt).toLocaleString()}</td>
                    <td><b>{TYPE_LABEL[h.type] ?? h.type}</b></td>
                    <td>{h.changedQty}</td>
                    <td>{h.currentStocks}</td>
                    <td className="text-muted">{h.orderId ? `#${h.orderId}` : '-'}</td>
                    <td className="text-muted" style={{ fontSize: 12 }}>{h.reason ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </AdminShell>
  );
}
