import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getCategories, createCategory, createChildCategory, updateCategory, deleteCategory } from '../../api/categories';
import { ApiError } from '../../api/client';
import { useToast } from '../../components/Toast';
import { AdminShell } from './AdminLayout';
import type { CategoryResponse } from '../../api/types';

export function AdminCategoriesPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [name, setName] = useState('');                       // 새 대분류명
  const [childDraft, setChildDraft] = useState<{ parentId: number; name: string } | null>(null); // 카드 안 중분류 입력
  const [editId, setEditId] = useState<number | null>(null);
  const [editName, setEditName] = useState('');

  const { data: categories, isLoading } = useQuery({ queryKey: ['categories'], queryFn: getCategories });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['categories'] });
  const onError = (e: unknown, fallback: string) =>
    toast(e instanceof ApiError ? e.message : fallback, 'error');

  const createParentMutation = useMutation({
    mutationFn: () => createCategory(name.trim()),
    onSuccess: () => { invalidate(); setName(''); toast('대분류가 생성되었어요. 이제 카드에서 중분류를 추가해보세요.'); },
    onError: (e) => onError(e, '생성에 실패했어요.')
  });

  const createChildMutation = useMutation({
    mutationFn: () => createChildCategory(childDraft!.parentId, childDraft!.name.trim()),
    onSuccess: () => { invalidate(); setChildDraft(null); toast('중분류가 생성되었어요. 이제 상품을 등록할 수 있어요.'); },
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
  const childrenOf = (id: number) => (categories ?? []).filter((c) => c.parentCategory === id);

  function NameOrEdit({ category, bold }: { category: CategoryResponse; bold?: boolean }) {
    if (editId === category.categoryId) {
      return (
        <span className="row" style={{ gap: 8 }}>
          <input
            className="input"
            value={editName}
            onChange={(e) => setEditName(e.target.value)}
            style={{ padding: '7px 12px', width: 150 }}
            autoFocus
          />
          <button className="btn btn-primary btn-sm" disabled={updateMutation.isPending} onClick={() => updateMutation.mutate()}>저장</button>
          <button className="btn btn-ghost btn-sm" onClick={() => setEditId(null)}>취소</button>
        </span>
      );
    }
    return (
      <span className="row" style={{ gap: 8 }}>
        <span style={{ fontWeight: bold ? 800 : 600, fontSize: bold ? 16 : 13.5 }}>{category.categoryName}</span>
        <button className="btn btn-ghost btn-sm" style={{ padding: '4px 10px', fontSize: 11 }}
          onClick={() => { setEditId(category.categoryId); setEditName(category.categoryName); }}>수정</button>
        <button className="btn btn-danger btn-sm" style={{ padding: '4px 10px', fontSize: 11 }}
          onClick={() => { if (confirm(`"${category.categoryName}"를 삭제할까요?`)) deleteMutation.mutate(category.categoryId); }}>삭제</button>
      </span>
    );
  }

  return (
    <AdminShell title="카테고리 관리">
      {/* 구조 안내 — 상품은 중분류에만 등록된다는 규칙을 화면에 드러냄 */}
      <p className="text-muted" style={{ marginBottom: 18, fontSize: 13.5 }}>
        카테고리는 <b>대분류 &gt; 중분류</b> 2단계 구조이고, <b style={{ color: 'var(--buy)' }}>상품은 중분류에 등록</b>돼요.
        대분류를 만든 뒤 카드 안에서 중분류를 추가하세요.
      </p>

      {/* 새 대분류 */}
      <form
        className="row rise"
        style={{ marginBottom: 24 }}
        onSubmit={(e) => {
          e.preventDefault();
          if (!name.trim()) return toast('대분류명을 입력해주세요.', 'error');
          createParentMutation.mutate();
        }}
      >
        <input className="input" placeholder="새 대분류명 (예: 신발)" value={name} onChange={(e) => setName(e.target.value)} style={{ flex: 1, maxWidth: 320 }} />
        <button className="btn btn-primary btn-sm" disabled={createParentMutation.isPending}>대분류 만들기</button>
      </form>

      {isLoading ? (
        <div className="spin" />
      ) : parents.length === 0 ? (
        <div className="empty">
          <div className="empty-mark">C</div>
          아직 카테고리가 없어요. 먼저 대분류를 만들어보세요.
        </div>
      ) : (
        <div className="stack">
          {parents.map((parent) => {
            const children = childrenOf(parent.categoryId);
            const drafting = childDraft?.parentId === parent.categoryId;
            return (
              <div key={parent.categoryId} className="panel rise" style={{ padding: 20 }}>
                <div className="row between" style={{ flexWrap: 'wrap', gap: 10 }}>
                  <NameOrEdit category={parent} bold />
                  <span className="text-muted" style={{ fontSize: 12 }}>중분류 {children.length}개</span>
                </div>

                <div className="divider" style={{ margin: '14px 0' }} />

                <div className="stack" style={{ gap: 8 }}>
                  {children.length === 0 && !drafting && (
                    <p className="text-muted" style={{ fontSize: 13 }}>
                      중분류가 없어요 — 아직 이 대분류엔 상품을 등록할 수 없어요.
                    </p>
                  )}
                  {children.map((child) => (
                    <div key={child.categoryId} className="row" style={{ paddingLeft: 14, borderLeft: '2px solid var(--hairline)' }}>
                      <NameOrEdit category={child} />
                    </div>
                  ))}

                  {drafting ? (
                    <form
                      className="row"
                      style={{ paddingLeft: 14 }}
                      onSubmit={(e) => {
                        e.preventDefault();
                        if (!childDraft?.name.trim()) return toast('중분류명을 입력해주세요.', 'error');
                        createChildMutation.mutate();
                      }}
                    >
                      <input
                        className="input"
                        placeholder={`${parent.categoryName}의 중분류명 (예: 스니커즈)`}
                        value={childDraft.name}
                        onChange={(e) => setChildDraft({ parentId: parent.categoryId, name: e.target.value })}
                        style={{ width: 240, padding: '9px 14px' }}
                        autoFocus
                      />
                      <button className="btn btn-primary btn-sm" disabled={createChildMutation.isPending}>추가</button>
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => setChildDraft(null)}>취소</button>
                    </form>
                  ) : (
                    <button
                      className="btn btn-ghost btn-sm"
                      style={{ alignSelf: 'flex-start', marginLeft: 14 }}
                      onClick={() => setChildDraft({ parentId: parent.categoryId, name: '' })}
                    >
                      + 중분류 추가
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </AdminShell>
  );
}
