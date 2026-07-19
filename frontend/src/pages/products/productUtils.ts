import type { Product } from '../../api/products';

interface ProductFilters {
  search?: string;
  enabled?: boolean;
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
    return [
      product.productCode,
      product.productName,
      product.categoryName,
      product.parentCategoryName,
      product.brand,
    ]
      .filter((value): value is string => Boolean(value))
      .some((value) => value.toLocaleLowerCase().includes(keyword));
  });
}
