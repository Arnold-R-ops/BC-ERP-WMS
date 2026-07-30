import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import {
  DrawerForm,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Modal,
  Pagination,
  Select,
} from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { listProductSkus } from '../../api/productSkus';
import type { PurchaseOrderPayload } from '../../api/purchasing';
import { listSuppliers } from '../../api/suppliers';

const PURCHASE_LINES_PAGE_SIZE = 20;

export type PurchaseOrderFormValues = Omit<PurchaseOrderPayload, 'operatorId' | 'operatorName'>;

interface PurchaseOrderFormDrawerProps {
  loading: boolean;
  open: boolean;
  onClose: () => void;
  onSubmit: (values: PurchaseOrderFormValues) => Promise<boolean>;
}

interface PurchaseLine {
  expiryDate?: string;
  externalBatchCode?: string;
  productSkuId?: number;
  productionDate?: string;
  remark?: string;
  orderedQuantity?: number;
  unitCost?: number;
}

interface PurchaseLineBulkPatch {
  expiryDate?: string;
  productionDate?: string;
  remark?: string;
  unitCost?: number;
}

export interface PurchaseLineSummary {
  lineCount: number;
  totalCost: number;
  totalQuantity: number;
}

export interface PurchaseLinePageRange {
  currentPage: number;
  endIndex: number;
  startIndex: number;
  totalPages: number;
}

export interface PurchaseLineValidationIssue {
  field: 'orderedQuantity' | 'productSkuId';
  index: number;
  type: 'duplicateProduct' | 'productRequired' | 'quantityRequired';
}

export function summarizePurchaseLines(lines: PurchaseLine[] = []): PurchaseLineSummary {
  return lines.reduce<PurchaseLineSummary>((summary, line) => {
    const quantity = Number(line.orderedQuantity) || 0;
    const unitCost = Number(line.unitCost) || 0;
    return {
      lineCount: summary.lineCount + 1,
      totalCost: summary.totalCost + quantity * unitCost,
      totalQuantity: summary.totalQuantity + quantity,
    };
  }, { lineCount: 0, totalCost: 0, totalQuantity: 0 });
}

export function getPurchaseLinePageRange(
  totalLines: number,
  requestedPage: number,
  pageSize = PURCHASE_LINES_PAGE_SIZE,
): PurchaseLinePageRange {
  const totalPages = Math.max(1, Math.ceil(totalLines / pageSize));
  const currentPage = Math.min(Math.max(1, requestedPage), totalPages);
  const startIndex = (currentPage - 1) * pageSize;
  return {
    currentPage,
    endIndex: Math.min(totalLines, startIndex + pageSize),
    startIndex,
    totalPages,
  };
}

export function applyPurchaseLineBulkPatch(
  lines: PurchaseLine[],
  selectedIndexes: number[],
  patch: PurchaseLineBulkPatch,
): PurchaseLine[] {
  const selected = new Set(selectedIndexes);
  const definedPatch = Object.fromEntries(
    Object.entries(patch).filter(([, value]) => value !== undefined && value !== ''),
  ) as PurchaseLineBulkPatch;
  return lines.map((line, index) => (
    selected.has(index) ? { ...line, ...definedPatch } : line
  ));
}

export function validatePurchaseLines(lines: PurchaseLine[] = []): PurchaseLineValidationIssue | null {
  const skuIndexes = new Map<number, number>();
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    if (!line.productSkuId) {
      return { field: 'productSkuId', index, type: 'productRequired' };
    }
    if (!line.orderedQuantity || Number(line.orderedQuantity) < 1) {
      return { field: 'orderedQuantity', index, type: 'quantityRequired' };
    }
    const firstIndex = skuIndexes.get(line.productSkuId);
    if (firstIndex !== undefined) {
      return { field: 'productSkuId', index, type: 'duplicateProduct' };
    }
    skuIndexes.set(line.productSkuId, index);
  }
  return null;
}

export function PurchaseOrderFormDrawer({
  loading,
  open,
  onClose,
  onSubmit,
}: PurchaseOrderFormDrawerProps): JSX.Element {
  const [form] = Form.useForm<PurchaseOrderFormValues>();
  const { t } = useTranslation();
  const [bulkEditorOpen, setBulkEditorOpen] = useState(false);
  const [bulkPatch, setBulkPatch] = useState<PurchaseLineBulkPatch>({});
  const [linePage, setLinePage] = useState(1);
  const [selectedLineKeys, setSelectedLineKeys] = useState<number[]>([]);
  const productSkusQuery = useQuery({
    queryKey: ['product-skus', 'active'],
    queryFn: () => listProductSkus({ enabledOnly: true }),
    enabled: open,
  });
  const suppliersQuery = useQuery({
    queryKey: ['suppliers', 'active'],
    queryFn: () => listSuppliers(true),
    enabled: open,
  });
  const productSkuOptions = useMemo(() => (productSkusQuery.data ?? [])
    .filter((productSku): productSku is typeof productSku & { id: number } => productSku.id !== undefined)
    .map((productSku) => ({
      label: `${productSku.productName ?? productSku.name ?? '-'} · ${productSku.skuName ?? productSku.specification ?? '-'} (${productSku.skuCode ?? productSku.barcode ?? '-'})`,
      value: productSku.id,
    })), [productSkusQuery.data]);
  const supplierOptions = (suppliersQuery.data ?? []).map((supplier) => ({
    label: `${supplier.name ?? '-'} (${supplier.code ?? '-'})`,
    value: supplier.id,
  }));
  const watchedItems = Form.useWatch('items', form) ?? [];
  const summary = summarizePurchaseLines(watchedItems);
  const selectedSkuIds = new Set(watchedItems
    .map((item) => item?.productSkuId)
    .filter((id): id is number => id !== undefined));

  const submit = async (values: PurchaseOrderFormValues): Promise<boolean> => {
    const validationIssue = validatePurchaseLines(values.items);
    if (validationIssue) {
      setLinePage(Math.floor(validationIssue.index / PURCHASE_LINES_PAGE_SIZE) + 1);
      form.setFields([{
        errors: [t(`purchasing.validation.${validationIssue.type}`)],
        name: ['items', validationIssue.index, validationIssue.field],
      }]);
      return false;
    }
    const saved = await onSubmit(values);
    if (saved) form.resetFields();
    return saved;
  };

  return (
    <DrawerForm<PurchaseOrderFormValues>
      drawerProps={{ destroyOnHidden: true, maskClosable: false }}
      form={form}
      initialValues={{ items: [{ orderedQuantity: 1 }] }}
      onFinish={submit}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          form.resetFields();
          setBulkEditorOpen(false);
          setBulkPatch({});
          setLinePage(1);
          setSelectedLineKeys([]);
          onClose();
        }
      }}
      open={open}
      submitter={{
        searchConfig: { resetText: t('common.cancel'), submitText: t('common.create') },
        submitButtonProps: { loading },
      }}
      title={t('purchasing.create')}
      width={1280}
    >
      {suppliersQuery.isError && (
        <Alert
          action={<Button onClick={() => void suppliersQuery.refetch()} size="small">{t('common.retry')}</Button>}
          message={t('purchasing.messages.supplierLoadFailed')}
          showIcon
          type="error"
        />
      )}
      <ProFormSelect
        fieldProps={{ loading: suppliersQuery.isLoading, optionFilterProp: 'label', showSearch: true }}
        label={t('purchasing.fields.supplier')}
        name="supplierId"
        options={supplierOptions}
        rules={[{ required: true, message: t('purchasing.validation.supplierRequired') }]}
        width="md"
      />
      <ProFormText fieldProps={{ type: 'date' }} label={t('purchasing.fields.expectedDate')} name="expectedDate" width="sm" />
      <ProFormTextArea fieldProps={{ maxLength: 500, showCount: true }} label={t('common.remark')} name="remark" />

      <section className="purchase-lines-section">
        <div className="purchase-lines-heading">
          <div>
            <strong>{t('purchasing.form.items')}</strong>
            <span>{t('purchasing.form.itemsHint')}</span>
          </div>
          <div className="purchase-lines-summary" aria-live="polite">
            <span>{t('purchasing.form.lineCount', { count: summary.lineCount })}</span>
            <span>{t('purchasing.form.totalQuantity', { count: summary.totalQuantity })}</span>
            <strong>{t('purchasing.form.totalCost', { amount: summary.totalCost.toFixed(2) })}</strong>
          </div>
        </div>

        {productSkusQuery.isError && (
          <Alert
            action={<Button onClick={() => void productSkusQuery.refetch()} size="small">{t('common.retry')}</Button>}
            message={t('purchasing.messages.skuLoadFailed')}
            showIcon
            type="error"
          />
        )}

        <Form.List
          name="items"
          rules={[{
            validator: async (_, items) => {
              if (!items || items.length < 1) {
                throw new Error(t('purchasing.validation.itemRequired'));
              }
            },
          }]}
        >
          {(fields, { add, remove }, { errors }) => (
            (() => {
              const pageRange = getPurchaseLinePageRange(fields.length, linePage);
              const visibleFields = fields.slice(pageRange.startIndex, pageRange.endIndex);
              const visibleKeys = visibleFields.map((field) => field.key);
              const selectedKeys = new Set(selectedLineKeys);
              const selectedVisibleCount = visibleKeys.filter((key) => selectedKeys.has(key)).length;
              const selectedIndexes = fields
                .filter((field) => selectedKeys.has(field.key))
                .map((field) => field.name);

              const toggleVisibleLines = (checked: boolean): void => {
                setSelectedLineKeys((current) => {
                  const next = new Set(current);
                  visibleKeys.forEach((key) => (checked ? next.add(key) : next.delete(key)));
                  return [...next];
                });
              };

              const deleteSelectedLines = (): void => {
                if (selectedIndexes.length === 0 || selectedIndexes.length === fields.length) return;
                remove(selectedIndexes);
                const remainingCount = fields.length - selectedIndexes.length;
                setLinePage(Math.min(pageRange.currentPage, Math.max(1, Math.ceil(remainingCount / PURCHASE_LINES_PAGE_SIZE))));
                setSelectedLineKeys([]);
              };

              const applyBulkPatch = (): void => {
                const lines = (form.getFieldValue('items') ?? []) as PurchaseLine[];
                form.setFieldValue('items', applyPurchaseLineBulkPatch(lines, selectedIndexes, bulkPatch));
                setBulkEditorOpen(false);
                setBulkPatch({});
              };

              return (
                <>
                  <div className="purchase-lines-toolbar">
                    <Checkbox
                      aria-label={t('purchasing.bulk.selectPage')}
                      checked={visibleFields.length > 0 && selectedVisibleCount === visibleFields.length}
                      indeterminate={selectedVisibleCount > 0 && selectedVisibleCount < visibleFields.length}
                      onChange={(event) => toggleVisibleLines(event.target.checked)}
                    >
                      {t('purchasing.bulk.selectPage')}
                    </Checkbox>
                    <span>{t('purchasing.bulk.selected', { count: selectedIndexes.length })}</span>
                    <Button
                      aria-label={t('purchasing.bulk.edit')}
                      disabled={selectedIndexes.length === 0}
                      icon={<EditOutlined />}
                      onClick={() => setBulkEditorOpen(true)}
                      size="small"
                    >
                      {t('purchasing.bulk.edit')}
                    </Button>
                    <Button
                      aria-label={t('purchasing.bulk.delete')}
                      danger
                      disabled={selectedIndexes.length === 0 || selectedIndexes.length === fields.length}
                      icon={<DeleteOutlined />}
                      onClick={deleteSelectedLines}
                      size="small"
                    >
                      {t('purchasing.bulk.delete')}
                    </Button>
                  </div>

                  <div className="purchase-lines-table">
                    <div className="purchase-line-grid purchase-line-header" aria-hidden="true">
                      <span />
                      <span>{t('purchasing.fields.product')}</span>
                      <span>{t('purchasing.fields.orderedQuantity')}</span>
                      <span>{t('purchasing.fields.unitCost')}</span>
                      <span>{t('purchasing.fields.expiryDate')}</span>
                      <span>{t('purchasing.fields.productionDate')}</span>
                      <span>{t('purchasing.fields.externalBatchCode')}</span>
                      <span>{t('common.remark')}</span>
                      <span>{t('purchasing.form.subtotal')}</span>
                      <span />
                    </div>
                    {visibleFields.map((field) => {
                      const index = field.name;
                      const currentSkuId = watchedItems[index]?.productSkuId;
                      const lineQuantity = Number(watchedItems[index]?.orderedQuantity) || 0;
                      const lineUnitCost = Number(watchedItems[index]?.unitCost) || 0;
                      const options = productSkuOptions.map((option) => ({
                        ...option,
                        disabled: selectedSkuIds.has(option.value) && option.value !== currentSkuId,
                      }));
                      return (
                        <div className="purchase-line-grid purchase-line-row" key={field.key}>
                          <Checkbox
                            aria-label={t('purchasing.bulk.selectLine', { index: index + 1 })}
                            checked={selectedKeys.has(field.key)}
                            onChange={(event) => {
                              setSelectedLineKeys((current) => (
                                event.target.checked
                                  ? [...new Set([...current, field.key])]
                                  : current.filter((key) => key !== field.key)
                              ));
                            }}
                          />
                          <Form.Item
                            name={[field.name, 'productSkuId']}
                            rules={[
                              { required: true, message: t('purchasing.validation.productRequired') },
                              ({ getFieldValue }) => ({
                                validator: async (_, value) => {
                                  if (!value) return;
                                  const duplicateCount = (getFieldValue('items') ?? [])
                                    .filter((item: PurchaseLine) => item?.productSkuId === value).length;
                                  if (duplicateCount > 1) {
                                    throw new Error(t('purchasing.validation.duplicateProduct'));
                                  }
                                },
                              }),
                            ]}
                          >
                            <Select
                              aria-label={t('purchasing.fields.product')}
                              loading={productSkusQuery.isLoading}
                              optionFilterProp="label"
                              options={options}
                              showSearch
                            />
                          </Form.Item>
                          <Form.Item
                            name={[field.name, 'orderedQuantity']}
                            rules={[{ required: true, message: t('purchasing.validation.quantityRequired') }]}
                          >
                            <InputNumber aria-label={t('purchasing.fields.orderedQuantity')} min={1} precision={0} />
                          </Form.Item>
                          <Form.Item name={[field.name, 'unitCost']}>
                            <InputNumber aria-label={t('purchasing.fields.unitCost')} min={0} precision={2} />
                          </Form.Item>
                          <Form.Item name={[field.name, 'expiryDate']}>
                            <Input aria-label={t('purchasing.fields.expiryDate')} type="date" />
                          </Form.Item>
                          <Form.Item name={[field.name, 'productionDate']}>
                            <Input aria-label={t('purchasing.fields.productionDate')} type="date" />
                          </Form.Item>
                          <Form.Item name={[field.name, 'externalBatchCode']}>
                            <Input aria-label={t('purchasing.fields.externalBatchCode')} maxLength={100} />
                          </Form.Item>
                          <Form.Item name={[field.name, 'remark']}>
                            <Input aria-label={t('common.remark')} maxLength={500} />
                          </Form.Item>
                          <strong className="purchase-line-subtotal">{(lineQuantity * lineUnitCost).toFixed(2)}</strong>
                          <Button
                            aria-label={t('purchasing.form.removeItem', { index: index + 1 })}
                            danger
                            disabled={fields.length === 1}
                            icon={<DeleteOutlined />}
                            onClick={() => {
                              remove(field.name);
                              setSelectedLineKeys((current) => current.filter((key) => key !== field.key));
                            }}
                            type="text"
                          />
                        </div>
                      );
                    })}
                  </div>

                  <div className="purchase-lines-footer">
                    <Button
                      aria-label={t('purchasing.form.addItem')}
                      icon={<PlusOutlined />}
                      onClick={() => {
                        add({ orderedQuantity: 1 });
                        setLinePage(Math.ceil((fields.length + 1) / PURCHASE_LINES_PAGE_SIZE));
                      }}
                      type="dashed"
                    >
                      {t('purchasing.form.addItem')}
                    </Button>
                    <Pagination
                      current={pageRange.currentPage}
                      onChange={setLinePage}
                      pageSize={PURCHASE_LINES_PAGE_SIZE}
                      showSizeChanger={false}
                      showTotal={(total) => t('purchasing.bulk.totalLines', { count: total })}
                      total={fields.length}
                    />
                  </div>
                  <Form.ErrorList errors={errors} />

                  <Modal
                    okButtonProps={{
                      'aria-label': t('purchasing.bulk.apply'),
                      disabled: selectedIndexes.length === 0,
                    }}
                    okText={t('purchasing.bulk.apply')}
                    onCancel={() => {
                      setBulkEditorOpen(false);
                      setBulkPatch({});
                    }}
                    onOk={applyBulkPatch}
                    open={bulkEditorOpen}
                    title={t('purchasing.bulk.title', { count: selectedIndexes.length })}
                  >
                    <p className="purchase-bulk-hint">{t('purchasing.bulk.hint')}</p>
                    <div className="purchase-bulk-grid">
                      <label>
                        <span>{t('purchasing.fields.unitCost')}</span>
                        <InputNumber
                          aria-label={t('purchasing.bulk.unitCost')}
                          min={0}
                          onChange={(value) => setBulkPatch((current) => ({ ...current, unitCost: value ?? undefined }))}
                          precision={2}
                          value={bulkPatch.unitCost}
                        />
                      </label>
                      <label>
                        <span>{t('purchasing.fields.productionDate')}</span>
                        <Input
                          aria-label={t('purchasing.bulk.productionDate')}
                          onChange={(event) => setBulkPatch((current) => ({ ...current, productionDate: event.target.value }))}
                          type="date"
                          value={bulkPatch.productionDate}
                        />
                      </label>
                      <label>
                        <span>{t('purchasing.fields.expiryDate')}</span>
                        <Input
                          aria-label={t('purchasing.bulk.expiryDate')}
                          onChange={(event) => setBulkPatch((current) => ({ ...current, expiryDate: event.target.value }))}
                          type="date"
                          value={bulkPatch.expiryDate}
                        />
                      </label>
                      <label className="purchase-bulk-remark">
                        <span>{t('common.remark')}</span>
                        <Input
                          aria-label={t('purchasing.bulk.remark')}
                          maxLength={500}
                          onChange={(event) => setBulkPatch((current) => ({ ...current, remark: event.target.value }))}
                          value={bulkPatch.remark}
                        />
                      </label>
                    </div>
                  </Modal>
                </>
              );
            })()
          )}
        </Form.List>
      </section>
    </DrawerForm>
  );
}
