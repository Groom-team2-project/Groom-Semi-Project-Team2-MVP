import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getProducts, createProduct, updateProduct, deleteProduct } from '../../api/products';
import { getCategoryTree } from '../../api/categories';
import { ApiError } from '../../api/client';
import { formatPrice } from '../../components/ProductCard';
import { useToast } from '../../components/Toast';
import { AdminShell } from './AdminLayout';

export function AdminProductsPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  // 등록 폼
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [stocks, setStocks] = useState('');
  const [categoryId, setCategoryId] = useState('');

  // 수정 상태
  const [editId, setEditId] = useState<number | null>(null);
  const [editName, setEditName] = useState('');
  const [editPrice, setEditPrice] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['admin-products', page],
    queryFn: () => getProducts({ page, size: 10 })
  });
  const { data: categories } = useQuery({ queryKey: ['categories'], queryFn: getCategoryTree });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-products'] });
    queryClient.invalidateQueries({ queryKey: ['products'] });
  };

  const onError = (e: unknown, fallback: string) =>
    toast(e instanceof ApiError ? e.message : fallback, 'error');

  const createMutation = useMutation({
    mutationFn: () =>
      createProduct({
        productName: name.trim(),
        productPrice: Number(price),
        stocks: Number(stocks),
        categoryId: categoryId ? Number(categoryId) : null
      }),
    onSuccess: () => {
      invalidate();
      setName(''); setPrice(''); setStocks('');
      toast('상품이 등록되었어요.');
    },
    onError: (e) => onError(e, '상품 등록에 실패했어요.')
  });

  const updateMutation = useMutation({
    mutationFn: () => updateProduct(editId!, { productName: editName.trim(), productPrice: Number(editPrice) }),
    onSuccess: () => {
      invalidate();
      setEditId(null);
      toast('상품이 수정되었어요.');
    },
    onError: (e) => onError(e, '수정에 실패했어요.')
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteProduct(id),
    onSuccess: () => { invalidate(); toast('상품이 삭제되었어요.'); },
    onError: (e) => onError(e, '삭제에 실패했어요.')
  });

  const totalPages = data ? Math.max(1, Math.ceil(data.totalElements / data.size)) : 1;

  return (
    <AdminShell title="상품 관리">
      <div className="bezel rise rise-1" style={{ marginBottom: 24 }}>
        <div className="core">
          <h2 className="h-section" style={{ marginBottom: 14 }}>새 상품 등록</h2>
          <form
            className="row"
            style={{ flexWrap: 'wrap' }}
            onSubmit={(e) => {
              e.preventDefault();
              if (!name.trim() || !price || !stocks) return toast('상품명·가격·재고를 입력해주세요.', 'error');
              createMutation.mutate();
            }}
          >
            <input className="input" placeholder="상품명" value={name} onChange={(e) => setName(e.target.value)} style={{ flex: 2, minWidth: 160 }} />
            <input className="input" placeholder="가격" type="number" min={0} value={price} onChange={(e) => setPrice(e.target.value)} style={{ flex: 1, minWidth: 110 }} />
            <input className="input" placeholder="초기 재고" type="number" min={0} value={stocks} onChange={(e) => setStocks(e.target.value)} style={{ flex: 1, minWidth: 110 }} />
            {/* 상품은 중분류에만 등록 가능(백엔드 규칙) — 대분류별로 그룹핑해 노출 */}
            <select className="input" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} style={{ minWidth: 170 }}>
              <option value="">카테고리 없음</option>
              {(categories ?? [])
                .filter((p) => p.parentCategory == null)
                .map((parent) => {
                  const kids = (categories ?? []).filter((c) => c.parentCategory === parent.categoryId);
                  if (kids.length === 0) return null;
                  return (
                    <optgroup key={parent.categoryId} label={parent.categoryName}>
                      {kids.map((c) => (
                        <option key={c.categoryId} value={c.categoryId}>{c.categoryName}</option>
                      ))}
                    </optgroup>
                  );
                })}
            </select>
            <button className="btn btn-primary btn-sm" disabled={createMutation.isPending}>등록</button>
          </form>
          {(categories ?? []).length > 0 && !(categories ?? []).some((c) => c.parentCategory != null) && (
            <p className="text-muted" style={{ marginTop: 12, fontSize: 13 }}>
              상품은 <b>중분류</b>에 등록돼요. 아직 중분류가 없어서 카테고리 없이만 등록할 수 있어요 —{' '}
              <Link to="/admin/categories" style={{ color: 'var(--buy)', fontWeight: 700 }}>
                카테고리 탭에서 중분류 만들기
              </Link>
            </p>
          )}
        </div>
      </div>

      {isLoading ? (
        <div className="spin" />
      ) : !data || data.content.length === 0 ? (
        <div className="empty">등록된 상품이 없어요.</div>
      ) : (
        <div className="bezel rise rise-2">
          <div className="core" style={{ padding: 8, overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr><th>ID</th><th>상품명</th><th>가격</th><th style={{ width: 190 }}>액션</th></tr>
              </thead>
              <tbody>
                {data.content.map((p) => (
                  <tr key={p.productId}>
                    <td className="text-muted">{p.productId}</td>
                    <td>
                      {editId === p.productId ? (
                        <input className="input" value={editName} onChange={(e) => setEditName(e.target.value)} style={{ padding: '8px 12px' }} />
                      ) : (
                        <b>{p.productName}</b>
                      )}
                    </td>
                    <td>
                      {editId === p.productId ? (
                        <input className="input" type="number" value={editPrice} onChange={(e) => setEditPrice(e.target.value)} style={{ padding: '8px 12px', width: 110 }} />
                      ) : (
                        formatPrice(p.productPrice)
                      )}
                    </td>
                    <td>
                      {editId === p.productId ? (
                        <div className="row">
                          <button className="btn btn-primary btn-sm" disabled={updateMutation.isPending} onClick={() => updateMutation.mutate()}>저장</button>
                          <button className="btn btn-ghost btn-sm" onClick={() => setEditId(null)}>취소</button>
                        </div>
                      ) : (
                        <div className="row">
                          <button
                            className="btn btn-ghost btn-sm"
                            onClick={() => { setEditId(p.productId); setEditName(p.productName); setEditPrice(String(p.productPrice)); }}
                          >수정</button>
                          <button
                            className="btn btn-danger btn-sm"
                            onClick={() => { if (confirm(`"${p.productName}" 상품을 삭제할까요?`)) deleteMutation.mutate(p.productId); }}
                          >삭제</button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {totalPages > 1 && (
        <div className="row" style={{ justifyContent: 'center', marginTop: 24 }}>
          <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
          <span className="text-muted">{page + 1} / {totalPages}</span>
          <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>다음</button>
        </div>
      )}
    </AdminShell>
  );
}
