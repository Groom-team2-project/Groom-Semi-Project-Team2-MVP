import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getCategories, createCategory, createChildCategory, updateCategory, deleteCategory } from '../../api/categories';
import { ApiError } from '../../api/client';
import { useToast } from '../../components/Toast';
import { AdminShell } from './AdminLayout';

export function AdminCategoriesPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState('');
  const [editId, setEditId] = useState<number | null>(null);
  const [editName, setEditName] = useState('');

  const { data: categories, isLoading } = useQuery({ queryKey: ['categories'], queryFn: getCategories });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['categories'] });
  const onError = (e: unknown, fallback: string) =>
    toast(e instanceof ApiError ? e.message : fallback, 'error');

  const createMutation = useMutation({
    mutationFn: () =>
      parentId
        ? createChildCategory(Number(parentId), name.trim())
        : createCategory(name.trim()),
    onSuccess: () => { invalidate(); setName(''); toast('카테고리가 생성되었어요.'); },
    onError: (e) => onError(e, '생성에 실패했어요.')
  });

  const updateMutation = useMutation({
    mutationFn: () => updateCategory(editId!, editName.trim()),
    onSuccess: () => { invalidate(); setEditId(null); toast('수정되었어요.'); },
    onError: (e) => onError(e, '수정에 실패했어요.')
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteCategory(id),
    onSuccess: () => { invalidate(); toast('삭제되었어요.'); },
    onError: (e) => onError(e, '삭제에 실패했어요. (하위 카테고리나 상품이 있으면 삭제할 수 없어요)')
  });

  const parents = (categories ?? []).filter((c) => c.parentCategory == null);
  const nameOf = (id: number | null) =>
    (categories ?? []).find((c) => c.categoryId === id)?.categoryName ?? '-';

  return (
    <AdminShell title="카테고리 관리">
      <div className="bezel rise rise-1" style={{ marginBottom: 24 }}>
        <div className="core">
          <h2 className="h-section" style={{ marginBottom: 14 }}>새 카테고리</h2>
          <form
            className="row"
            style={{ flexWrap: 'wrap' }}
            onSubmit={(e) => {
              e.preventDefault();
              if (!name.trim()) return toast('카테고리명을 입력해주세요.', 'error');
              createMutation.mutate();
            }}
          >
            <input className="input" placeholder="카테고리명" value={name} onChange={(e) => setName(e.target.value)} style={{ flex: 1, minWidth: 160 }} />
            <select className="input" value={parentId} onChange={(e) => setParentId(e.target.value)} style={{ minWidth: 170 }}>
              <option value="">대분류로 생성</option>
              {parents.map((c) => (
                <option key={c.categoryId} value={c.categoryId}>{c.categoryName}의 하위로</option>
              ))}
            </select>
            <button className="btn btn-primary btn-sm" disabled={createMutation.isPending}>생성</button>
          </form>
        </div>
      </div>

      {isLoading ? (
        <div className="spin" />
      ) : (categories ?? []).length === 0 ? (
        <div className="empty">카테고리가 없어요. 먼저 대분류를 만들어보세요.</div>
      ) : (
        <div className="bezel rise rise-2">
          <div className="core" style={{ padding: 8, overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr><th>ID</th><th>이름</th><th>상위</th><th style={{ width: 170 }}>액션</th></tr>
              </thead>
              <tbody>
                {(categories ?? []).map((c) => (
                  <tr key={c.categoryId}>
                    <td className="text-muted">{c.categoryId}</td>
                    <td>
                      {editId === c.categoryId ? (
                        <input className="input" value={editName} onChange={(e) => setEditName(e.target.value)} style={{ padding: '8px 12px' }} />
                      ) : (
                        <b>{c.categoryName}</b>
                      )}
                    </td>
                    <td className="text-muted">{c.parentCategory == null ? '대분류' : nameOf(c.parentCategory)}</td>
                    <td>
                      {editId === c.categoryId ? (
                        <div className="row">
                          <button className="btn btn-primary btn-sm" disabled={updateMutation.isPending} onClick={() => updateMutation.mutate()}>저장</button>
                          <button className="btn btn-ghost btn-sm" onClick={() => setEditId(null)}>취소</button>
                        </div>
                      ) : (
                        <div className="row">
                          <button className="btn btn-ghost btn-sm" onClick={() => { setEditId(c.categoryId); setEditName(c.categoryName); }}>수정</button>
                          <button className="btn btn-danger btn-sm" onClick={() => { if (confirm(`"${c.categoryName}"를 삭제할까요?`)) deleteMutation.mutate(c.categoryId); }}>삭제</button>
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
    </AdminShell>
  );
}
