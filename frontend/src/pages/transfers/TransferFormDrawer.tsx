import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Drawer, Form, Input, InputNumber, Select, Space } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  listTransferBatches,
  type Transfer,
  type TransferDraftPayload,
  type TransferReferences,
} from '../../api/transfers';

interface TransferFormDrawerProps {
  initial?: Transfer;
  loading: boolean;
  onClose: () => void;
  onSubmit: (payload: TransferDraftPayload) => Promise<void>;
  open: boolean;
  references?: TransferReferences;
}

interface TransferLineEditorProps {
  fieldName: number;
  form: ReturnType<typeof Form.useForm<TransferDraftPayload>>[0];
  references?: TransferReferences;
  sourceWarehouseId?: number;
}

export type TransferBackorderValidationIssue = 'DUPLICATE_BACKORDER' | 'ETA_REQUIRED' | undefined;

export function getTransferBackorderValidationIssue(payload: TransferDraftPayload): TransferBackorderValidationIssue {
  const linkedBackorders = payload.lines
    .map((line) => line.backorderLineId)
    .filter((id): id is number => id !== undefined && id !== null);
  if (new Set(linkedBackorders).size !== linkedBackorders.length) return 'DUPLICATE_BACKORDER';
  if (linkedBackorders.length > 0 && !payload.estimatedArrivalAt) return 'ETA_REQUIRED';
  return undefined;
}

function TransferLineEditor({
  fieldName, form, references, sourceWarehouseId,
}: TransferLineEditorProps): JSX.Element {
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';
  const productSkuId = Form.useWatch(['lines', fieldName, 'productSkuId'], form);
  const batches = useQuery({
    queryKey: ['transfer-batches', sourceWarehouseId, productSkuId],
    queryFn: () => listTransferBatches(sourceWarehouseId as number, productSkuId as number),
    enabled: Boolean(sourceWarehouseId && productSkuId),
  });
  const backorders = (references?.backorders ?? []).filter((item) => item.productSkuId === productSkuId);

  return (
    <div className="transfer-form-line-fields">
      <Form.Item
        label="SKU"
        name={[fieldName, 'productSkuId']}
        rules={[{ required: true, message: english ? 'Select a SKU.' : '请选择 SKU' }]}
      >
        <Select
          onChange={() => {
            form.setFieldValue(['lines', fieldName, 'preferredBatchId'], undefined);
            form.setFieldValue(['lines', fieldName, 'backorderLineId'], undefined);
          }}
          optionFilterProp="label"
          options={(references?.skus ?? []).map((sku) => ({
            label: `${sku.skuCode} · ${sku.name}`,
            value: sku.id,
          }))}
          showSearch
        />
      </Form.Item>
      <Form.Item
        label={english ? 'Planned quantity' : '计划数量'}
        name={[fieldName, 'quantity']}
        rules={[{ required: true, message: english ? 'Enter quantity.' : '请输入数量' }]}
      >
        <InputNumber min={1} precision={0} />
      </Form.Item>
      <Form.Item label={english ? 'Preferred batch (optional)' : '优先批次（可选）'} name={[fieldName, 'preferredBatchId']}>
        <Select
          allowClear
          loading={batches.isLoading}
          optionFilterProp="label"
          options={(batches.data ?? []).map((batch) => ({
            label: `${batch.batchCode} · ${batch.locationCode} · ${english ? 'available' : '可用'} ${batch.availableQuantity}`,
            value: batch.id,
          }))}
          placeholder={english ? 'Blank = system FEFO' : '留空由系统按 FEFO 分配'}
          showSearch
        />
      </Form.Item>
      <Form.Item label={english ? 'Target backorder (optional)' : '目标仓销售欠货（可选）'} name={[fieldName, 'backorderLineId']}>
        <Select
          allowClear
          disabled={backorders.length === 0}
          options={backorders.map((item) => ({
            label: `${english ? 'Sales order' : '销售单'} #${item.salesOrderId} · ${english ? 'promised' : '承诺'} ${item.promisedDate ?? '-'} · ${english ? 'remaining' : '剩余'} ${item.remainingQuantity}`,
            value: item.id,
          }))}
        />
      </Form.Item>
    </div>
  );
}

export function TransferFormDrawer({
  initial, loading, onClose, onSubmit, open, references,
}: TransferFormDrawerProps): JSX.Element {
  const [form] = Form.useForm<TransferDraftPayload>();
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';
  const sourceWarehouseId = Form.useWatch('sourceWarehouseId', form);

  useEffect(() => {
    if (!open) return;
    if (!initial) {
      form.resetFields();
      form.setFieldsValue({ lines: [{} as TransferDraftPayload['lines'][number]] });
      return;
    }
    form.setFieldsValue({
      sourceWarehouseId: initial.sourceWarehouseId,
      destinationWarehouseId: initial.destinationWarehouseId,
      reason: initial.reason,
      estimatedArrivalAt: initial.estimatedArrivalAt?.slice(0, 16),
      lines: initial.lines.map((line) => ({
        productSkuId: line.productSkuId,
        quantity: line.requestedQuantity,
        preferredBatchId: line.preferredBatchId ?? undefined,
        backorderLineId: line.backorderLineId ?? undefined,
      })),
    });
  }, [form, initial, open]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    if (values.sourceWarehouseId === values.destinationWarehouseId) {
      form.setFields([{ name: 'destinationWarehouseId', errors: [english ? 'Warehouses must differ.' : '调出仓和调入仓不能相同'] }]);
      return;
    }
    const backorderIssue = getTransferBackorderValidationIssue(values);
    if (backorderIssue === 'DUPLICATE_BACKORDER') {
      form.setFields([{ name: 'lines', errors: [english ? 'Each backorder can be linked only once.' : '同一欠货明细只能关联一次'] }]);
      return;
    }
    if (backorderIssue === 'ETA_REQUIRED') {
      form.setFields([{ name: 'estimatedArrivalAt', errors: [english ? 'Estimated arrival is required for a linked backorder.' : '关联销售欠货时必须填写预计到达时间'] }]);
      return;
    }
    await onSubmit(values);
  };

  const warehouseOptions = (references?.warehouses ?? []).map((warehouse) => ({
    label: `${warehouse.code} · ${warehouse.name}`,
    value: warehouse.id,
  }));

  return (
    <Drawer
      destroyOnClose
      extra={<Space><Button onClick={onClose}>{english ? 'Cancel' : '取消'}</Button><Button loading={loading} onClick={() => void submit()} type="primary">{english ? 'Save draft' : '保存草稿'}</Button></Space>}
      onClose={onClose}
      open={open}
      title={initial ? (english ? 'Edit transfer draft' : '编辑调拨草稿') : (english ? 'New warehouse transfer' : '新建仓库调拨')}
      width="min(100%, 1040px)"
    >
      <Alert
        message={english ? 'Only AVAILABLE stock can be allocated. Leave preferred batches blank to let the system allocate by FEFO after approval.' : '普通调拨只分配 AVAILABLE 库存；优先批次留空时，批准后由系统按 FEFO 分配。'}
        showIcon
        style={{ marginBottom: 16 }}
        type="info"
      />
      <Form form={form} layout="vertical">
        <div className="transfer-form-header-grid">
          <Form.Item label={english ? 'Source warehouse' : '调出仓库'} name="sourceWarehouseId" rules={[{ required: true }]}>
            <Select
              onChange={() => {
                const lines = (form.getFieldValue('lines') ?? []) as TransferDraftPayload['lines'];
                form.setFieldValue('lines', lines.map((line) => ({ ...line, preferredBatchId: undefined })));
              }}
              options={warehouseOptions}
              showSearch
              optionFilterProp="label"
            />
          </Form.Item>
          <Form.Item label={english ? 'Destination warehouse' : '调入仓库'} name="destinationWarehouseId" rules={[{ required: true }]}>
            <Select options={warehouseOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item label={english ? 'Estimated arrival' : '预计到达时间'} name="estimatedArrivalAt">
            <Input type="datetime-local" />
          </Form.Item>
        </div>
        <Form.Item label={english ? 'Transfer reason' : '调拨原因'} name="reason" rules={[{ required: true }]}>
          <Input.TextArea maxLength={500} rows={2} showCount />
        </Form.Item>
        <Form.List name="lines" rules={[{ validator: async (_, lines) => { if (!lines?.length) throw new Error(english ? 'Add at least one line.' : '至少添加一条商品明细'); } }]}>
          {(fields, { add, remove }, { errors }) => (
            <>
              {fields.map((field) => (
                <div className="transfer-form-line" key={field.key}>
                  <TransferLineEditor
                    fieldName={field.name}
                    form={form}
                    references={references}
                    sourceWarehouseId={sourceWarehouseId}
                  />
                  <Button aria-label={english ? 'Remove line' : '删除明细'} danger icon={<DeleteOutlined />} onClick={() => remove(field.name)} />
                </div>
              ))}
              <Button icon={<PlusOutlined />} onClick={() => add()}>{english ? 'Add SKU' : '添加商品'}</Button>
              <Form.ErrorList errors={errors} />
            </>
          )}
        </Form.List>
      </Form>
    </Drawer>
  );
}
