import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addProductImage,
  createProduct,
  deleteProduct,
  deleteProductImage,
  getProduct,
  getProducts,
  updateProduct,
  updateProductImage
} from '../../api/products';
import type { ImageResponse, ProductDetail } from '../../api/types';
import { getCategoryTree } from '../../api/categories';
import { ApiError } from '../../api/client';
import { formatPrice } from '../../components/ProductCard';
import { useToast } from '../../components/Toast';
import { AdminShell } from './AdminLayout';

function useObjectUrl(file: File | null) {
  const [url, setUrl] = useState('');

  useEffect(() => {
    if (!file) {
      setUrl('');
      return;
    }
    const nextUrl = URL.createObjectURL(file);
    setUrl(nextUrl);
    return () => URL.revokeObjectURL(nextUrl);
  }, [file]);

  return url;
}

function DetailImagesModal({
  productId,
  productName,
  onClose
}: {
  productId: number;
  productName: string;
  onClose: () => void;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const addInputRef = useRef<HTMLInputElement>(null);
  const { data: product, isLoading } = useQuery({
    queryKey: ['product', productId],
    queryFn: () => getProduct(productId)
  });

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [onClose]);

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['product', productId] });
  };
  const onError = (e: unknown, fallback: string) =>
    toast(e instanceof ApiError ? e.message : fallback, 'error');

  const addMutation = useMutation({
    mutationFn: (file: File) => addProductImage(productId, file),
    onSuccess: () => {
      refresh();
      if (addInputRef.current) addInputRef.current.value = '';
      toast('상세 이미지가 등록되었어요.');
    },
    onError: (e) => onError(e, '상세 이미지 등록에 실패했어요.')
  });
  const updateMutation = useMutation({
    mutationFn: ({ imageId, file }: { imageId: number; file: File }) =>
      updateProductImage(productId, imageId, file),
    onSuccess: () => {
      refresh();
      toast('상세 이미지가 교체되었어요.');
    },
    onError: (e) => onError(e, '상세 이미지 수정에 실패했어요.')
  });
  const deleteMutation = useMutation({
    mutationFn: (imageId: number) => deleteProductImage(productId, imageId),
    onSuccess: () => {
      refresh();
      toast('상세 이미지가 삭제되었어요.');
    },
    onError: (e) => onError(e, '상세 이미지 삭제에 실패했어요.')
  });

  const images = product?.detailImages ?? [];
  const busy = addMutation.isPending || updateMutation.isPending || deleteMutation.isPending;

  return (
    <div className="image-modal-backdrop" role="presentation" onMouseDown={(e) => {
      if (e.target === e.currentTarget) onClose();
    }}>
      <section className="image-modal" role="dialog" aria-modal="true" aria-labelledby="detail-image-title">
        <div className="image-modal-head">
          <div>
            <span className="eyebrow">Product Images</span>
            <h2 id="detail-image-title" className="h-section">{productName} 상세 이미지</h2>
            <p className="text-muted">이미지는 최대 10개까지 등록할 수 있어요. 이미지를 누르면 바로 교체됩니다.</p>
          </div>
          <button className="modal-close" type="button" aria-label="상세 이미지 창 닫기" onClick={onClose}>×</button>
        </div>

        <div className="detail-image-toolbar">
          <label className={`btn btn-primary btn-sm ${images.length >= 10 || busy ? 'is-disabled' : ''}`}>
            + 이미지 등록
            <input
              ref={addInputRef}
              className="visually-hidden"
              type="file"
              accept="image/*"
              disabled={images.length >= 10 || busy}
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) addMutation.mutate(file);
              }}
            />
          </label>
          <span className="image-count">{images.length} / 10</span>
        </div>

        {isLoading ? (
          <div className="spin" />
        ) : images.length === 0 ? (
          <div className="empty detail-image-empty">아직 등록된 상세 이미지가 없어요.</div>
        ) : (
          <div className="detail-image-grid">
            {images.map((image: ImageResponse) => (
              <article className="detail-image-item" key={image.imageId}>
                <label className="detail-image-replace" title="클릭해서 다른 이미지로 교체">
                  <img src={image.detailImage} alt={`${productName} 상세 이미지`} />
                  <span>클릭해서 교체</span>
                  <input
                    className="visually-hidden"
                    type="file"
                    accept="image/*"
                    disabled={busy}
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) updateMutation.mutate({ imageId: image.imageId, file });
                      e.currentTarget.value = '';
                    }}
                  />
                </label>
                <button
                  className="btn btn-danger btn-sm"
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    if (confirm('이 상세 이미지를 삭제할까요?')) deleteMutation.mutate(image.imageId);
                  }}
                >
                  삭제
                </button>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

export function AdminProductsPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [stocks, setStocks] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [mainImage, setMainImage] = useState<File | null>(null);
  const mainImagePreview = useObjectUrl(mainImage);

  const [editId, setEditId] = useState<number | null>(null);
  const [editName, setEditName] = useState('');
  const [editPrice, setEditPrice] = useState('');
  const [editCategoryId, setEditCategoryId] = useState('');
  const [editImage, setEditImage] = useState<File | null>(null);
  const editImagePreview = useObjectUrl(editImage);
  const [detailProduct, setDetailProduct] = useState<{ id: number; name: string } | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['admin-products', page],
    queryFn: () => getProducts({ page, size: 10 })
  });
  const { data: categories } = useQuery({ queryKey: ['categories'], queryFn: getCategoryTree });
  const productDetails = useQueries({
    queries: (data?.content ?? []).map((product) => ({
      queryKey: ['product', product.productId],
      queryFn: () => getProduct(product.productId),
      staleTime: 30_000
    }))
  });
  const detailById = new Map<number, ProductDetail>();
  (data?.content ?? []).forEach((product, index) => {
    const detail = productDetails[index]?.data;
    if (detail) detailById.set(product.productId, detail);
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-products'] });
    queryClient.invalidateQueries({ queryKey: ['products'] });
    queryClient.invalidateQueries({ queryKey: ['product'] });
  };
  const onError = (e: unknown, fallback: string) =>
    toast(e instanceof ApiError ? e.message : fallback, 'error');

  const createMutation = useMutation({
    mutationFn: () =>
      createProduct({
        productName: name.trim(),
        productPrice: Number(price),
        stocks: Number(stocks),
        categoryId: Number(categoryId)
      }, mainImage!),
    onSuccess: () => {
      invalidate();
      setName('');
      setPrice('');
      setStocks('');
      setCategoryId('');
      setMainImage(null);
      toast('상품이 등록되었어요.');
    },
    onError: (e) => onError(e, '상품 등록에 실패했어요.')
  });

  const updateMutation = useMutation({
    mutationFn: () => updateProduct(editId!, {
      productName: editName.trim(),
      productPrice: Number(editPrice),
      categoryId: Number(editCategoryId)
    }, editImage),
    onSuccess: () => {
      invalidate();
      setEditId(null);
      setEditImage(null);
      toast('상품이 수정되었어요.');
    },
    onError: (e) => onError(e, '수정에 실패했어요.')
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteProduct(id),
    onSuccess: () => {
      invalidate();
      toast('상품이 삭제되었어요.');
    },
    onError: (e) => onError(e, '삭제에 실패했어요.')
  });

  const startEdit = async (productId: number, productName: string, productPrice: number) => {
    setEditId(productId);
    setEditName(productName);
    setEditPrice(String(productPrice));
    setEditImage(null);
    const cached = detailById.get(productId);
    if (cached) {
      setEditCategoryId(String(cached.category));
      return;
    }
    try {
      const detail = await queryClient.fetchQuery({
        queryKey: ['product', productId],
        queryFn: () => getProduct(productId)
      });
      setEditCategoryId(String(detail.category));
    } catch (e) {
      setEditId(null);
      onError(e, '상품 정보를 불러오지 못했어요.');
    }
  };

  const totalPages = data ? Math.max(1, Math.ceil(data.totalElements / data.size)) : 1;
  const subCategories = (categories ?? []).filter((category) => category.parentCategory != null);

  return (
    <AdminShell title="상품 관리">
      <div className="bezel rise rise-1" style={{ marginBottom: 24 }}>
        <div className="core">
          <h2 className="h-section" style={{ marginBottom: 14 }}>새 상품 등록</h2>
          <form
            className="product-create-form"
            onSubmit={(e) => {
              e.preventDefault();
              if (!name.trim() || !price || !stocks || !categoryId) {
                return toast('상품명·가격·재고·카테고리를 입력해주세요.', 'error');
              }
              if (!mainImage) return toast('대표 이미지를 선택해주세요.', 'error');
              createMutation.mutate();
            }}
          >
            <div className="product-fields">
              <input className="input" placeholder="상품명" value={name} onChange={(e) => setName(e.target.value)} />
              <input className="input" placeholder="가격" type="number" min={1} value={price} onChange={(e) => setPrice(e.target.value)} />
              <input className="input" placeholder="초기 재고" type="number" min={1} value={stocks} onChange={(e) => setStocks(e.target.value)} />
              <select className="input" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                <option value="">중분류 선택</option>
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
            </div>

            <div className="main-image-picker">
              {mainImagePreview && <img src={mainImagePreview} alt="선택한 대표 이미지 미리보기" />}
              <label
                className="btn btn-ghost btn-sm has-tooltip"
                data-tooltip="대표 이미지는 하나만 등록할 수 있어요."
                title="대표 이미지는 하나만 등록할 수 있어요."
              >
                {mainImage ? '대표 이미지 변경' : '대표 이미지 선택'}
                <input
                  className="visually-hidden"
                  type="file"
                  accept="image/*"
                  onChange={(e) => setMainImage(e.target.files?.[0] ?? null)}
                />
              </label>
              <span className="file-name">{mainImage?.name ?? '선택된 파일 없음'}</span>
            </div>

            <button className="btn btn-primary btn-sm" disabled={createMutation.isPending}>등록</button>
          </form>
          {(categories ?? []).length > 0 && subCategories.length === 0 && (
            <p className="text-muted" style={{ marginTop: 12, fontSize: 13 }}>
              상품은 <b>중분류</b>에 등록돼요.{' '}
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
          <div className="core product-table-wrap">
            <table className="table product-admin-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>대표 이미지</th>
                  <th>상품명</th>
                  <th>가격</th>
                  <th style={{ width: 280 }}>액션</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((product) => {
                  const detail = detailById.get(product.productId);
                  const isEditing = editId === product.productId;
                  const imageUrl = isEditing && editImagePreview
                    ? editImagePreview
                    : detail?.productImage;

                  return (
                    <tr key={product.productId}>
                      <td className="text-muted">{product.productId}</td>
                      <td>
                        {isEditing ? (
                          <label className="admin-main-image editable" title="클릭해서 대표 이미지 변경">
                            {imageUrl
                              ? <img src={imageUrl} alt={`${product.productName} 대표 이미지`} />
                              : <span>이미지 선택</span>}
                            <span className="image-edit-badge">변경</span>
                            <input
                              className="visually-hidden"
                              type="file"
                              accept="image/*"
                              onChange={(e) => setEditImage(e.target.files?.[0] ?? null)}
                            />
                          </label>
                        ) : (
                          <div className="admin-main-image">
                            {imageUrl
                              ? <img src={imageUrl} alt={`${product.productName} 대표 이미지`} />
                              : <span>불러오는 중</span>}
                          </div>
                        )}
                      </td>
                      <td>
                        {isEditing ? (
                          <input className="input compact-input" value={editName} onChange={(e) => setEditName(e.target.value)} />
                        ) : (
                          <b>{product.productName}</b>
                        )}
                      </td>
                      <td>
                        {isEditing ? (
                          <input className="input compact-input price-input" type="number" min={1} value={editPrice} onChange={(e) => setEditPrice(e.target.value)} />
                        ) : (
                          formatPrice(product.productPrice)
                        )}
                      </td>
                      <td>
                        {isEditing ? (
                          <div className="edit-actions">
                            <select className="input compact-input category-edit" value={editCategoryId} onChange={(e) => setEditCategoryId(e.target.value)}>
                              <option value="">중분류 선택</option>
                              {subCategories.map((category) => (
                                <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>
                              ))}
                            </select>
                            <div className="row">
                              <button
                                className="btn btn-primary btn-sm"
                                disabled={updateMutation.isPending || !editName.trim() || !editPrice || !editCategoryId}
                                onClick={() => updateMutation.mutate()}
                              >
                                저장
                              </button>
                              <button className="btn btn-ghost btn-sm" onClick={() => { setEditId(null); setEditImage(null); }}>취소</button>
                            </div>
                          </div>
                        ) : (
                          <div className="row admin-actions">
                            <button className="btn btn-ghost btn-sm" onClick={() => startEdit(product.productId, product.productName, product.productPrice)}>수정</button>
                            <button className="btn btn-ghost btn-sm" onClick={() => setDetailProduct({ id: product.productId, name: product.productName })}>상세 이미지</button>
                            <button
                              className="btn btn-danger btn-sm"
                              onClick={() => {
                                if (confirm(`"${product.productName}" 상품을 삭제할까요?`)) deleteMutation.mutate(product.productId);
                              }}
                            >
                              삭제
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
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

      {detailProduct && (
        <DetailImagesModal
          productId={detailProduct.id}
          productName={detailProduct.name}
          onClose={() => setDetailProduct(null)}
        />
      )}
    </AdminShell>
  );
}
