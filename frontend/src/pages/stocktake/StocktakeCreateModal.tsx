import { useQuery } from '@tanstack/react-query';
import { Alert, Form, Modal, Select } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { CreateStocktakePayload, StocktakeCycleType } from '../../api/stocktake';
import { listActiveWarehouses } from '../../api/masterData';

interface StocktakeCreateModalProps {
  loading: boolean;
  onCancel: () => void;
  onConfirm: (payload: CreateStocktakePayload) => Promise<void>;
  open: boolean;
}

export function StocktakeCreateModal({
  loading,
  onCancel,
  onConfirm,
  open,
}: StocktakeCreateModalProps): JSX.Element {
  const [form] = Form.useForm<CreateStocktakePayload>();
  const { t } = useTranslation();
  const warehousesQuery = useQuery({
    queryKey: ['active-warehouses'],
    queryFn: listActiveWarehouses,
    enabled: open,
  });

  useEffect(() => {
    if (open) form.setFieldsValue({ cycleType: 'ADHOC' });
    else form.resetFields();
  }, [form, open]);

  const cycleTypes: StocktakeCycleType[] = ['ADHOC', 'MONTHLY', 'QUARTERLY', 'ANNUAL'];

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      destroyOnHidden
      okText={t('stocktake.actions.create')}
      onCancel={onCancel}
      onOk={() => form.submit()}
      open={open}
      title={t('stocktake.create.title')}
    >
      <Alert
        description={t('stocktake.create.snapshotNotice')}
        message={t('stocktake.create.snapshotTitle')}
        showIcon
        type="warning"
      />
      <Form form={form} layout="vertical" onFinish={onConfirm} style={{ marginTop: 16 }}>
        <Form.Item
          label={t('stocktake.fields.warehouse')}
          name="warehouseId"
          rules={[{ required: true, message: t('stocktake.validation.warehouseRequired') }]}
        >
          <Select
            loading={warehousesQuery.isLoading}
            options={(warehousesQuery.data ?? [])
              .filter((warehouse) => warehouse.id !== undefined)
              .map((warehouse) => ({ label: `${warehouse.code ?? '-'} · ${warehouse.name ?? '-'}`, value: warehouse.id }))}
            placeholder={t('stocktake.create.warehousePlaceholder')}
            showSearch
            optionFilterProp="label"
          />
        </Form.Item>
        <Form.Item
          label={t('stocktake.fields.cycleType')}
          name="cycleType"
          rules={[{ required: true }]}
        >
          <Select options={cycleTypes.map((value) => ({ label: t(`stocktake.cycles.${value}`), value }))} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
