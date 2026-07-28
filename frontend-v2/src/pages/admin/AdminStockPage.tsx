import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getProducts } from '../../api/products';
import { getStock, stockIn, getStockHistories } from '../../api/stock';
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
    queryFn: () => getProducts({ page: 0, size: 100 })
  });

  const { data: stock } = useQuery({
    queryKey: ['stock', productId],
    queryFn: () => getStock(productId),
    enabled: Boolean(productId)
  });

  const { data: histories, isLoading: historiesLoading } = useQuery({
    queryKey: ['stock-histories', productId],
    queryFn: () => getStockHistories(productId),
    enabled: Boolean(productId)
  });

  const stockInMutation = useMutation({
    mutationFn: () => stockIn(productId, Number(quantity), reason.trim() || '관리자 입고'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock', productId] });
      queryClient.invalidateQueries({ queryKey: ['stock-histories', productId] });
      setQuantity('');
      toast('입고 처리되었어요.');
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : '입고에 실패했어요.', 'error')
  });

  return (
    <AdminShell title="재고 관리">
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
              {(products?.content ?? []).map((p) => (
                <option key={p.productId} value={p.productId}>#{p.productId} {p.productName}</option>
              ))}
            </select>
            <input className="input" placeholder="수량" type="number" min={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} style={{ flex: 1, minWidth: 100 }} />
            <input className="input" placeholder="사유" value={reason} onChange={(e) => setReason(e.target.value)} style={{ flex: 1, minWidth: 130 }} />
            <button className="btn btn-primary btn-sm" disabled={stockInMutation.isPending}>입고</button>
          </form>
          {stock && (
            <p className="text-muted" style={{ marginTop: 12, fontSize: 13 }}>
              현재 <b>{stock.productName}</b> 실재고: <b>{stock.stocks}개</b>
            </p>
          )}
        </div>
      </div>

      {!productId ? (
        <div className="empty">상품을 선택하면 재고 변동 이력이 보여요.</div>
      ) : historiesLoading ? (
        <div className="spin" />
      ) : (
        <div className="bezel rise rise-2">
          <div className="core" style={{ padding: 8, overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr><th>시각</th><th>유형</th><th>변동</th><th>현재고</th><th>주문</th><th>사유</th></tr>
              </thead>
              <tbody>
                {(histories ?? []).length === 0 && (
                  <tr><td colSpan={6} className="text-muted" style={{ textAlign: 'center' }}>이력이 없어요.</td></tr>
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
