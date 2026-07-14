import type { Product } from '../../api/products';

interface ProductFilters {
  search?: string;
  enabled?: boolean;
}

export interface SpuOption {
  label: string;
  value: number;
}

export function filterProducts(products: Product[], filters: ProductFilters): Product[] {
  const keyword = filters.search?.trim().toLocaleLowerCase();

  return products.filter((product) => {
    if (filters.enabled !== undefined && product.enabled !== filters.enabled) {
      return false;
    }

    if (!keyword) {
      return true;
    }

    return [product.name, product.skuName, product.barcode]
      .filter((value): value is string => Boolean(value))
      .some((value) => value.toLocaleLowerCase().includes(keyword));
  });
}

export function getSpuOptions(products: Product[]): SpuOption[] {
  const options = new Map<number, string>();
  products.forEach((product) => {
    if (product.spuId !== undefined) {
      options.set(
        product.spuId,
        `${product.spuName ?? `SPU ${product.spuId}`} (#${product.spuId})`,
      );
    }
  });

  return Array.from(options, ([value, label]) => ({ value, label })).sort((left, right) =>
    left.label.localeCompare(right.label),
  );
}
