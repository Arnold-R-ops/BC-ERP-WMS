import type { Category } from '../../api/categories';

const SYSTEM_CATEGORY_CODES = new Set(['UNCATEGORIZED', 'PENDING_CLASSIFICATION']);

export function flattenCategories(categories: Category[]): Category[] {
  return categories.flatMap((category) => [category, ...flattenCategories(category.children ?? [])]);
}

export function findCategory(categories: Category[], categoryId?: number): Category | undefined {
  if (categoryId === undefined) return undefined;
  return flattenCategories(categories).find((category) => category.id === categoryId);
}

export function getCategoryPath(categories: Category[], categoryId?: number): Category[] {
  const byId = new Map(flattenCategories(categories).flatMap((category) =>
    category.id === undefined ? [] : [[category.id, category] as const],
  ));
  const path: Category[] = [];
  const visited = new Set<number>();
  let current = categoryId === undefined ? undefined : byId.get(categoryId);

  while (current?.id !== undefined && !visited.has(current.id)) {
    path.unshift(current);
    visited.add(current.id);
    current = current.parentId === undefined ? undefined : byId.get(current.parentId);
  }
  return path;
}

export function isSystemCategory(categories: Category[], category?: Category): boolean {
  return getCategoryPath(categories, category?.id).some((item) =>
    item.categoryCode ? SYSTEM_CATEGORY_CODES.has(item.categoryCode) : false,
  );
}

export function getCategoryScopeIds(category?: Category): Set<number> {
  if (!category) return new Set();
  return new Set(
    flattenCategories([category]).flatMap((item) => item.id === undefined ? [] : [item.id]),
  );
}

export function filterCategoryTree(categories: Category[], search?: string): Category[] {
  const keyword = search?.trim().toLocaleLowerCase();
  if (!keyword) return categories;

  return categories.flatMap((category) => {
    const children = filterCategoryTree(category.children ?? [], keyword);
    const matches = [category.categoryCode, category.categoryName]
      .filter((value): value is string => Boolean(value))
      .some((value) => value.toLocaleLowerCase().includes(keyword));
    return matches || children.length > 0 ? [{ ...category, children }] : [];
  });
}

export function reorderCategorySiblings(
  categories: Category[],
  draggedId: number,
  targetId: number,
  placeAfter: boolean,
): Category[] | undefined {
  const dragged = findCategory(categories, draggedId);
  const target = findCategory(categories, targetId);
  if (!dragged || !target || dragged.parentId !== target.parentId || draggedId === targetId) return undefined;

  const siblings = dragged.parentId === undefined
    ? [...categories]
    : [...(findCategory(categories, dragged.parentId)?.children ?? [])];
  const sourceIndex = siblings.findIndex((item) => item.id === draggedId);
  if (sourceIndex < 0) return undefined;

  const [moved] = siblings.splice(sourceIndex, 1);
  const targetIndex = siblings.findIndex((item) => item.id === targetId);
  if (!moved || targetIndex < 0) return undefined;
  siblings.splice(targetIndex + (placeAfter ? 1 : 0), 0, moved);
  return siblings;
}
