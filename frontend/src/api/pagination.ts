export interface ProTablePageParams {
  current?: number;
  pageSize?: number;
}

export interface PageLike<T> {
  content?: T[];
  totalElements?: number;
}

export interface ProTablePage<T> {
  data: T[];
  success: true;
  total: number;
}

export function toSpringPage(params: ProTablePageParams): { page: number; size: number } {
  return {
    page: Math.max((params.current ?? 1) - 1, 0),
    size: Math.max(params.pageSize ?? 20, 1),
  };
}

export function toProTablePage<T>(page: PageLike<T>): ProTablePage<T> {
  return {
    data: page.content ?? [],
    success: true,
    total: page.totalElements ?? 0,
  };
}

export function paginateArray<T>(items: T[], params: ProTablePageParams): ProTablePage<T> {
  const { page, size } = toSpringPage(params);
  const start = page * size;
  return {
    data: items.slice(start, start + size),
    success: true,
    total: items.length,
  };
}
