import {
  AppstoreOutlined,
  DeleteOutlined,
  EditOutlined,
  LockOutlined,
  PlusOutlined,
  PoweroffOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  ConfigProvider,
  Empty,
  Input,
  Spin,
  Table,
  Tooltip,
  Tree,
  Typography,
  theme,
  type TableColumnsType,
  type TreeDataNode,
  type TreeProps,
} from 'antd';
import { useEffect, useMemo, useState, type Key } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  CATEGORIES_QUERY_KEY,
  createCategory,
  deleteCategory,
  getCategoryTree,
  moveCategory,
  setCategoryEnabled,
  updateCategory,
  type Category,
} from '../../api/categories';
import { getErrorMessage } from '../../api/errors';
import { PRODUCT_SKUS_QUERY_KEY, listProductSkus, type ProductSku } from '../../api/productSkus';
import { PRODUCTS_QUERY_KEY, listProducts, type Product } from '../../api/products';
import { CategoryFormModal, type CategoryFormValues } from './CategoryFormModal';
import {
  filterCategoryTree,
  findCategory,
  flattenCategories,
  getCategoryPath,
  getCategoryScopeIds,
  isSystemCategory,
  reorderCategorySiblings,
} from './categoryUtils';

interface CategoryTreeNode extends TreeDataNode {
  nodeType: 'category';
  category: Category;
  children?: CategoryTreeNode[];
}

interface CatalogRow {
  key: string;
  kind: 'product' | 'sku';
  product: Product;
  sku?: ProductSku;
  children?: CatalogRow[];
}

function getCategoryKey(category: Category): string {
  return `category-${category.id ?? category.categoryCode ?? category.categoryName}`;
}

export function CategoryPage(): JSX.Element {
  const [search, setSearch] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<number>();
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Category>();
  const [defaultParentId, setDefaultParentId] = useState<number>();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { message, modal } = AntdApp.useApp();
  const { t } = useTranslation();

  const categoriesQuery = useQuery({
    queryKey: [...CATEGORIES_QUERY_KEY, 'tree'],
    queryFn: () => getCategoryTree(false),
  });
  const productsQuery = useQuery({ queryKey: PRODUCTS_QUERY_KEY, queryFn: () => listProducts() });
  const skusQuery = useQuery({ queryKey: PRODUCT_SKUS_QUERY_KEY, queryFn: () => listProductSkus() });

  const categories = categoriesQuery.data ?? [];
  const products = productsQuery.data ?? [];
  const skus = skusQuery.data ?? [];
  const flatCategories = useMemo(() => flattenCategories(categories), [categories]);
  const selectedCategory = useMemo(
    () => findCategory(categories, selectedCategoryId),
    [categories, selectedCategoryId],
  );
  const selectedPath = useMemo(
    () => getCategoryPath(categories, selectedCategory?.id),
    [categories, selectedCategory?.id],
  );

  useEffect(() => {
    if (flatCategories.length === 0) return;
    if (selectedCategoryId !== undefined && flatCategories.some((category) => category.id === selectedCategoryId)) return;
    const firstOperational = flatCategories.find((category) => !isSystemCategory(categories, category));
    setSelectedCategoryId(firstOperational?.id ?? flatCategories[0]?.id);
  }, [categories, flatCategories, selectedCategoryId]);

  useEffect(() => {
    if (expandedKeys.length > 0 || categories.length === 0) return;
    setExpandedKeys(categories.flatMap((category) => category.id === undefined ? [] : [getCategoryKey(category)]));
  }, [categories, expandedKeys.length]);

  const saveMutation = useMutation({
    mutationFn: async ({ category, values }: { category?: Category; values: CategoryFormValues }) => {
      if (category?.id === undefined) return createCategory(values);
      const updated = await updateCategory(category.id, {
        categoryName: values.categoryName,
        sortOrder: values.sortOrder,
        description: values.description,
      });
      return category.parentId === values.parentId ? updated : moveCategory(category.id, values.parentId);
    },
  });
  const statusMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => setCategoryEnabled(id, enabled),
  });
  const deleteMutation = useMutation({ mutationFn: deleteCategory });
  const sortMutation = useMutation({
    mutationFn: async (siblings: Category[]) => {
      await Promise.all(siblings.map((category, index) =>
        category.id === undefined
          ? Promise.resolve()
          : updateCategory(category.id, { sortOrder: (index + 1) * 10 }),
      ));
    },
  });

  const reloadCategories = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: CATEGORIES_QUERY_KEY });
  };
  const closeForm = (): void => {
    setFormOpen(false);
    setEditingCategory(undefined);
    setDefaultParentId(undefined);
  };
  const openCreateRoot = (): void => {
    setEditingCategory(undefined);
    setDefaultParentId(undefined);
    setFormOpen(true);
  };
  const openCreateChild = (parent: Category): void => {
    setEditingCategory(undefined);
    setDefaultParentId(parent.id);
    setFormOpen(true);
  };
  const openEdit = (category: Category): void => {
    setEditingCategory(category);
    setDefaultParentId(undefined);
    setFormOpen(true);
  };
  const saveCategory = async (values: CategoryFormValues): Promise<boolean> => {
    try {
      const saved = await saveMutation.mutateAsync({ category: editingCategory, values });
      message.success(editingCategory ? t('categories.messages.updated') : t('categories.messages.created'));
      closeForm();
      await reloadCategories();
      if (saved.id !== undefined) setSelectedCategoryId(saved.id);
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const productsForCategory = (category?: Category): Product[] => {
    const scope = getCategoryScopeIds(category);
    return products.filter((product) => product.categoryId !== undefined && scope.has(product.categoryId));
  };

  const changeStatus = async (category: Category): Promise<void> => {
    if (category.id === undefined) return;
    try {
      await statusMutation.mutateAsync({ id: category.id, enabled: !category.enabled });
      message.success(t('categories.messages.statusUpdated'));
      await reloadCategories();
    } catch (error) {
      message.error(getErrorMessage(error, t));
      throw error;
    }
  };
  const confirmStatusChange = (category: Category): void => {
    const relatedProducts = productsForCategory(category).length;
    const disabling = category.enabled !== false;
    modal.confirm({
      title: disabling ? t('categories.confirmations.deactivateTitle') : t('categories.confirmations.activateTitle'),
      content: disabling && relatedProducts > 0
        ? t('categories.confirmations.deactivateWithProducts', { count: relatedProducts })
        : disabling ? t('categories.confirmations.deactivate') : t('categories.confirmations.activate'),
      okText: disabling ? t('common.disabled') : t('common.enabled'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: disabling },
      onOk: () => changeStatus(category),
    });
  };
  const removeCategory = async (category: Category): Promise<void> => {
    if (category.id === undefined) return;
    try {
      await deleteMutation.mutateAsync(category.id);
      message.success(t('categories.messages.deleted'));
      await reloadCategories();
    } catch (error) {
      message.error(getErrorMessage(error, t));
      throw error;
    }
  };
  const confirmDelete = (category: Category): void => {
    modal.confirm({
      title: t('categories.confirmations.deleteTitle'),
      content: t('categories.confirmations.delete'),
      okText: t('common.delete'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: () => removeCategory(category),
    });
  };

  const filteredCategories = useMemo(() => filterCategoryTree(categories, search), [categories, search]);
  const visibleExpandedKeys = useMemo<Key[]>(() => search.trim()
    ? flattenCategories(filteredCategories).flatMap((category) => category.id === undefined ? [] : [getCategoryKey(category)])
    : expandedKeys,
  [expandedKeys, filteredCategories, search]);

  const treeData = useMemo<CategoryTreeNode[]>(() => {
    const buildNodes = (items: Category[]): CategoryTreeNode[] => items.flatMap((category) => {
      if (category.id === undefined) return [];
      const children = buildNodes(category.children ?? []);
      return [{
        key: getCategoryKey(category),
        title: category.categoryName ?? category.categoryCode ?? '-',
        nodeType: 'category',
        category,
        children,
      }];
    });
    return buildNodes(filteredCategories);
  }, [filteredCategories]);

  const renderTreeTitle = (node: CategoryTreeNode): React.ReactNode => {
    const category = node.category;
    const selected = category.id === selectedCategoryId;
    const system = isSystemCategory(categories, category);
    const relatedProducts = productsForCategory(category).length;

    return (
      <div className={`category-tree-row${selected ? ' category-tree-row-selected' : ''}`}>
        <Tooltip title={category.enabled === false ? t('common.disabled') : t('common.enabled')}>
          <span className={`category-status-dot${category.enabled === false ? ' category-status-dot-disabled' : ''}`} />
        </Tooltip>
        <span className="category-tree-name">{category.categoryName ?? '-'}</span>
        {system ? <span className="category-system-label"><LockOutlined /> {t('categories.workspace.system')}</span> : null}
        <span className="category-tree-count">{relatedProducts}</span>
      </div>
    );
  };

  const handleDrop: NonNullable<TreeProps<CategoryTreeNode>['onDrop']> = async (info) => {
    if (!info.dropToGap) return;
    const dragged = info.dragNode.category;
    const target = info.node.category;
    if (dragged?.id === undefined || target?.id === undefined) return;
    const targetPosition = Number(info.node.pos.split('-').at(-1));
    const reordered = reorderCategorySiblings(categories, dragged.id, target.id, info.dropPosition > targetPosition);
    if (!reordered) return;
    try {
      await sortMutation.mutateAsync(reordered);
      message.success(t('categories.messages.sorted'));
      await reloadCategories();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const selectedProducts = useMemo(() => productsForCategory(selectedCategory), [products, selectedCategory]);
  const selectedProductIds = useMemo(
    () => new Set(selectedProducts.flatMap((product) => product.id === undefined ? [] : [product.id])),
    [selectedProducts],
  );
  const selectedSkus = useMemo(
    () => skus.filter((sku) => sku.productId !== undefined && selectedProductIds.has(sku.productId)),
    [selectedProductIds, skus],
  );
  const catalogRows = useMemo<CatalogRow[]>(() => selectedProducts.map((product) => {
    const productSkus = skus.filter((sku) => sku.productId === product.id);
    return {
      key: `product-${product.id}`,
      kind: 'product',
      product,
      children: productSkus.length === 0 ? undefined : productSkus.map((sku) => ({
        key: `sku-${sku.id}`,
        kind: 'sku',
        product,
        sku,
      })),
    };
  }), [selectedProducts, skus]);

  const catalogColumns = useMemo<TableColumnsType<CatalogRow>>(() => [
    {
      title: t('categories.catalog.product'),
      key: 'catalog',
      width: 320,
      render: (_, row) => row.kind === 'product' ? (
        <div className="category-catalog-identity">
          <span className="category-kind-label category-kind-product">{t('categories.catalog.productKind')}</span>
          <div>
            <strong>{row.product.productName ?? '-'}</strong>
            <span>{row.product.productCode ?? '-'}</span>
          </div>
        </div>
      ) : (
        <div className="category-catalog-identity">
          <span className="category-kind-label category-kind-sku">SKU</span>
          <div>
            <strong>{row.sku?.skuName ?? row.sku?.name ?? '-'}</strong>
            <span>{row.sku?.specs ?? row.sku?.specification ?? '-'}</span>
          </div>
        </div>
      ),
    },
    {
      title: t('categories.catalog.skuCode'),
      key: 'skuCode',
      width: 190,
      render: (_, row) => row.kind === 'product'
        ? t('categories.catalog.skuCount', { count: row.children?.length ?? 0 })
        : row.sku?.skuCode ?? '-',
    },
    {
      title: t('categories.catalog.barcode'),
      key: 'barcode',
      width: 170,
      render: (_, row) => row.kind === 'sku' ? row.sku?.barcode ?? '-' : '-',
    },
    {
      title: t('categories.catalog.batchTracking'),
      key: 'tracking',
      width: 140,
      render: (_, row) => row.kind === 'sku'
        ? t(`productSkus.batchTracking.${row.sku?.batchTrackingMode ?? 'PRINTED_LABEL'}`)
        : '-',
    },
    {
      title: t('categories.fields.enabled'),
      key: 'enabled',
      width: 100,
      render: (_, row) => {
        const enabled = row.kind === 'product' ? row.product.enabled !== false : row.sku?.enabled !== false;
        return <span className="category-status"><span className={`category-status-dot${enabled ? '' : ' category-status-dot-disabled'}`} />{enabled ? t('common.enabled') : t('common.disabled')}</span>;
      },
    },
  ], [t]);

  const selectedSystem = isSystemCategory(categories, selectedCategory);
  const catalogLoading = productsQuery.isLoading || skusQuery.isLoading;
  const catalogError = productsQuery.isError || skusQuery.isError;

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          borderRadius: 6,
          colorBgBase: '#090b0c',
          colorBgContainer: '#111415',
          colorBorder: '#303536',
          colorPrimary: '#159b94',
          colorText: '#edf2f1',
          colorTextSecondary: '#9ca9a7',
        },
      }}
    >
      <section className="category-workspace">
        <header className="category-workspace-header">
          <div>
            <Typography.Title level={2}>{t('categories.title')}</Typography.Title>
            <p>{t('categories.workspace.subtitle')}</p>
          </div>
          <Button icon={<PlusOutlined />} onClick={openCreateRoot} type="primary">
            {t('categories.actions.addRoot')}
          </Button>
        </header>

        <div className="category-workspace-grid">
          <aside className="category-tree-panel">
            <div className="category-panel-heading">
              <div>
                <strong>{t('categories.workspace.hierarchyTitle')}</strong>
                <span>{t('categories.workspace.hierarchyHint')}</span>
              </div>
              <span>{flatCategories.length}</span>
            </div>
            <Input
              allowClear
              aria-label={t('categories.searchPlaceholder')}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('categories.searchPlaceholder')}
              prefix={<SearchOutlined />}
              value={search}
            />
            <div className="category-tree-scroll">
              {categoriesQuery.isLoading ? <Spin /> : categoriesQuery.isError ? (
                <Empty description={t('categories.workspace.loadFailed')}>
                  <Button onClick={() => void categoriesQuery.refetch()}>{t('common.retry')}</Button>
                </Empty>
              ) : (
                <Tree<CategoryTreeNode>
                  allowDrop={({ dragNode, dropNode, dropPosition }) => {
                    const dragged = dragNode.category;
                    const target = dropNode.category;
                    return dropPosition !== 0
                      && dragged?.parentId === target?.parentId
                      && !isSystemCategory(categories, dragged)
                      && !isSystemCategory(categories, target);
                  }}
                  blockNode
                  draggable={{
                    icon: false,
                    nodeDraggable: (node) => {
                      const category = (node as CategoryTreeNode).category;
                      return Boolean(category && !isSystemCategory(categories, category));
                    },
                  }}
                  expandedKeys={visibleExpandedKeys}
                  onDrop={(info) => void handleDrop(info)}
                  onExpand={setExpandedKeys}
                  onSelect={(_, info) => {
                    if (info.node.category?.id !== undefined) setSelectedCategoryId(info.node.category.id);
                  }}
                  selectedKeys={selectedCategory ? [getCategoryKey(selectedCategory)] : []}
                  showLine={{ showLeafIcon: false }}
                  titleRender={renderTreeTitle}
                  treeData={treeData}
                />
              )}
            </div>
            <div className="category-tree-legend">
              <span><i className="category-status-dot" />{t('common.enabled')}</span>
              <span><i className="category-status-dot category-status-dot-disabled" />{t('common.disabled')}</span>
              <span>{t('categories.workspace.dragHint')}</span>
            </div>
          </aside>

          <main className="category-detail-panel">
            {selectedCategory ? (
              <>
                <section className="category-detail-summary">
                  <div className="category-detail-title-row">
                    <div>
                      <p className="category-breadcrumb">{selectedPath.map((category) => category.categoryName).filter(Boolean).join(' / ')}</p>
                      <Typography.Title level={3}>{selectedCategory.categoryName ?? '-'}</Typography.Title>
                      <p className="category-description">{selectedCategory.description || t('categories.workspace.noDescription')}</p>
                    </div>
                    {!selectedSystem ? (
                      <div className="category-detail-actions">
                        {selectedCategory.level === 1 ? (
                          <Button icon={<PlusOutlined />} onClick={() => openCreateChild(selectedCategory)} type="primary">
                            {t('categories.actions.addChild')}
                          </Button>
                        ) : null}
                        <Button icon={<EditOutlined />} onClick={() => openEdit(selectedCategory)}>{t('common.edit')}</Button>
                        <Button
                          danger={selectedCategory.enabled !== false}
                          icon={<PoweroffOutlined />}
                          onClick={() => confirmStatusChange(selectedCategory)}
                        >
                          {selectedCategory.enabled === false ? t('common.enabled') : t('common.disabled')}
                        </Button>
                        <Button danger icon={<DeleteOutlined />} onClick={() => confirmDelete(selectedCategory)}>
                          {t('common.delete')}
                        </Button>
                      </div>
                    ) : <span className="category-system-lock"><LockOutlined /> {t('categories.workspace.systemManaged')}</span>}
                  </div>
                  <dl className="category-detail-metrics">
                    <div><dt>{t('categories.fields.code')}</dt><dd>{selectedCategory.categoryCode ?? '-'}</dd></div>
                    <div><dt>{t('categories.fields.sortOrder')}</dt><dd>{selectedCategory.sortOrder ?? 0}</dd></div>
                    <div><dt>{t('categories.workspace.productCount')}</dt><dd>{selectedProducts.length}</dd></div>
                    <div><dt>{t('categories.workspace.skuCount')}</dt><dd>{selectedSkus.length}</dd></div>
                    <div><dt>{t('categories.fields.enabled')}</dt><dd><span className="category-status"><span className={`category-status-dot${selectedCategory.enabled === false ? ' category-status-dot-disabled' : ''}`} />{selectedCategory.enabled === false ? t('common.disabled') : t('common.enabled')}</span></dd></div>
                  </dl>
                </section>

                <section className="category-catalog-section">
                  <div className="category-catalog-heading">
                    <div>
                      <Typography.Title level={4}>{t('categories.catalog.title')}</Typography.Title>
                      <p>{t('categories.catalog.subtitle')}</p>
                    </div>
                    <Button icon={<AppstoreOutlined />} onClick={() => navigate('/products')} type="text">
                      {t('categories.catalog.viewAll')}
                    </Button>
                  </div>
                  {catalogLoading ? <div className="category-catalog-state"><Spin /></div> : catalogError ? (
                    <Empty description={t('categories.catalog.loadFailed')}>
                      <Button onClick={() => { void productsQuery.refetch(); void skusQuery.refetch(); }}>{t('common.retry')}</Button>
                    </Empty>
                  ) : catalogRows.length === 0 ? (
                    <Empty description={t('categories.catalog.empty')} />
                  ) : (
                    <Table<CatalogRow>
                      columns={catalogColumns}
                      dataSource={catalogRows}
                      expandable={{
                        defaultExpandedRowKeys: selectedSkus.length <= 12 ? catalogRows.map((row) => row.key) : [],
                        indentSize: 22,
                      }}
                      key={selectedCategory.id}
                      pagination={false}
                      rowClassName={(row) => `category-catalog-row category-catalog-row-${row.kind}`}
                      rowKey="key"
                      scroll={{ x: 920 }}
                      size="middle"
                    />
                  )}
                </section>
              </>
            ) : <Empty description={t('categories.workspace.selectCategory')} />}
          </main>
        </div>

        <CategoryFormModal
          category={editingCategory}
          defaultParentId={defaultParentId}
          onClose={closeForm}
          onSubmit={saveCategory}
          open={formOpen}
          rootCategories={categories.filter((category) => !isSystemCategory(categories, category))}
          submitting={saveMutation.isPending}
        />
      </section>
    </ConfigProvider>
  );
}
