import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, App, Button, Input, InputNumber, Select, Space, Switch, Table, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { hasPermission } from '../../access';
import { useAuth } from '../../auth/AuthProvider';
import { getErrorMessage } from '../../api/errors';
import { downloadCsv } from '../../api/csv';
import type { ReorderSuggestion } from '../../api/dashboard';
import { listReplenishment, listReplenishmentWarehouses, saveWarehouseMinimum } from '../../api/replenishment';
import { createPurchaseOrder, updatePurchaseOrder, savePurchaseOrderAllocations, type PurchaseOrder } from '../../api/purchasing';
import { PurchaseOrderFormDrawer, type PurchaseOrderFormValues } from '../purchasing/PurchaseOrderFormDrawer';
import { persistPurchaseDraft, PurchaseAllocationPersistenceError } from '../purchasing/purchaseOrderPersistence';

export function ReplenishmentPage(): JSX.Element {
  const { t } = useTranslation();
  const { session } = useAuth();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [warehouseId, setWarehouseId] = useState<number>();
  const [all, setAll] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<React.Key[]>([]);
  const [initial, setInitial] = useState<Partial<PurchaseOrderFormValues>>();
  const [savedDraft, setSavedDraft] = useState<PurchaseOrder>();
  const [saving, setSaving] = useState(false);
  const warehouses = useQuery({ queryKey: ['replenishment-warehouses'], queryFn: listReplenishmentWarehouses });
  useEffect(() => {
    if (warehouses.data?.length === 1) setWarehouseId(warehouses.data[0].id);
  }, [warehouses.data]);
  const query = useQuery({ queryKey: ['replenishment', warehouseId, all], queryFn: () => listReplenishment(warehouseId, all) });
  const rows = (query.data ?? []).filter((row) =>
    [row.productName, row.barcode, row.supplier].some((value) => value?.toLowerCase().includes(keyword.toLowerCase())));
  const selectedRows = rows.filter(row => selected.includes(row.productSkuId!) && (row.suggestedReorderQuantity ?? 0) > 0);
  const warehouseName = warehouses.data?.find((warehouse) => warehouse.id === warehouseId)?.name ?? t('upgrade.replenishment.allWarehouses');
  const canCreate = hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:create')
    && hasPermission(session?.currentRole, session?.permissionCodes, 'purchase:update');
  const canEdit = hasPermission(session?.currentRole, session?.permissionCodes, 'product-sku:edit');
  const exportRows = (): void => downloadCsv('replenishment.csv', [
    [t('upgrade.replenishment.warehouse'), t('products.fields.barcode'), t('upgrade.replenishment.product'), t('upgrade.baseUnit'),
      t('upgrade.replenishment.available'), t('upgrade.replenishment.incoming'), t('upgrade.replenishment.minimum'),
      t('upgrade.replenishment.target'), t('upgrade.replenishment.suggested'), t('upgrade.replenishment.scope')],
    ...rows.map((row) => [warehouseName, row.barcode, row.productName, row.baseUnit, row.currentStock,
      row.outstandingPurchaseQuantity, row.thresholdConfigured ? row.minStock : '', row.targetStock,
      row.suggestedReorderQuantity, t('upgrade.replenishment.formula')]),
  ]);
  const openPurchase = (): void => {
    setSavedDraft(undefined);
    setInitial({ items: selectedRows.map((row) => ({
      productSkuId: row.productSkuId!, orderedQuantity: row.suggestedReorderQuantity!,
      allocations: [{ warehouseId, plannedQuantity: row.suggestedReorderQuantity! }],
    })) });
  };
  const save = async (values: PurchaseOrderFormValues): Promise<boolean> => {
    setSaving(true);
    try {
      await persistPurchaseDraft(values, savedDraft, { create: createPurchaseOrder, update: updatePurchaseOrder, saveAllocations: savePurchaseOrderAllocations });
      message.success(t('purchasing.messages.created'));
      setInitial(undefined); setSelected([]); setSavedDraft(undefined);
      navigate('/purchasing');
      return true;
    } catch (error) {
      if (error instanceof PurchaseAllocationPersistenceError) {
        setSavedDraft(error.order);
        message.error(t('purchasing.messages.draftSavedAllocationPending'));
      } else message.error(getErrorMessage(error, t));
      return false;
    } finally { setSaving(false); }
  };
  return <section>
    <Typography.Title level={2}>{t('upgrade.replenishment.title')}</Typography.Title>
    <Alert showIcon type="info" message={t('upgrade.replenishment.formula')} description={t('upgrade.replenishment.thresholdHint')} />
    <Space wrap style={{ margin: '16px 0' }}>
      <Select style={{ minWidth: 200 }} aria-label={t('upgrade.replenishment.warehouse')} value={warehouseId ?? 0}
        options={[{ value: 0, label: t('upgrade.replenishment.allWarehouses') }, ...(warehouses.data ?? []).map((w) => ({ value: w.id, label: w.name }))]}
        onChange={(value) => { setWarehouseId(value || undefined); setSelected([]); }} />
      <Input value={keyword} allowClear placeholder={t('upgrade.replenishment.search')} onChange={(event) => { setKeyword(event.target.value); setSelected([]); }} />
      <Switch checked={all} onChange={(value) => { setAll(value); setSelected([]); }} checkedChildren={t('upgrade.replenishment.allProducts')} unCheckedChildren={t('upgrade.replenishment.shortages')} />
      <Button onClick={() => void query.refetch()} loading={query.isFetching}>{t('common.refresh')}</Button>
      <Button disabled={!rows.length || query.isFetching || query.isError} onClick={exportRows}>{t('upgrade.exportCsv')}</Button>
      {canCreate && <Button type="primary" disabled={!warehouseId || !selectedRows.length || query.isFetching || query.isError} onClick={openPurchase}>{t('upgrade.replenishment.createPurchase')}</Button>}
    </Space>
    {(query.isError || warehouses.isError) && <Alert showIcon type="error" message={t('analytics.loadFailed')} />}
    <Table<ReorderSuggestion> rowKey={(row) => row.productSkuId!} dataSource={rows} loading={query.isFetching}
      rowSelection={canCreate ? { selectedRowKeys: selected, onChange: setSelected, getCheckboxProps: (row) => ({ disabled: !row.suggestedReorderQuantity || !warehouseId }) } : undefined}
      pagination={{ pageSize: 20 }} scroll={{ x: 1000 }} columns={[
        { title: t('upgrade.replenishment.product'), dataIndex: 'productName' },
        { title: t('products.fields.barcode'), dataIndex: 'barcode' },
        { title: t('upgrade.baseUnit'), dataIndex: 'baseUnit' },
        { title: t('upgrade.replenishment.available'), dataIndex: 'currentStock' },
        { title: t('upgrade.replenishment.incoming'), dataIndex: 'outstandingPurchaseQuantity' },
        { title: t('upgrade.replenishment.minimum'), render: (_, row) => warehouseId && canEdit
          ? <InputNumber key={String(row.productSkuId) + '-' + String(row.minStock) + '-' + String(row.thresholdConfigured)} min={0} precision={0}
              aria-label={t('upgrade.replenishment.minimum') + ' ' + row.productName}
              defaultValue={row.thresholdConfigured ? row.minStock : undefined} placeholder={t('upgrade.replenishment.unset')}
              onBlur={async (event) => {
                if (event.target.value === '') return;
                const value = Number(event.target.value);
                if (!Number.isInteger(value) || value < 0 || (row.thresholdConfigured && value === row.minStock)) return;
                try { await saveWarehouseMinimum(row.productSkuId!, warehouseId, value); await query.refetch(); }
                catch (error) { message.error(getErrorMessage(error, t)); }
              }} />
          : row.thresholdConfigured ? row.minStock : t('upgrade.replenishment.unset') },
        { title: t('upgrade.replenishment.target'), dataIndex: 'targetStock' },
        { title: t('upgrade.replenishment.suggested'), dataIndex: 'suggestedReorderQuantity' },
      ]} />
    <PurchaseOrderFormDrawer open={initial !== undefined} initialValues={initial} loading={saving}
      onClose={() => { setInitial(undefined); setSavedDraft(undefined); }} onSubmit={save} />
  </section>;
}
