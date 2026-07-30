import { Alert, Descriptions, Form, Input, InputNumber, Modal, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { ConfirmPickingPayload, OutboundTask } from '../../api/outbound';
import { getProductSku, PRODUCT_SKUS_QUERY_KEY } from '../../api/productSkus';

interface ConfirmFormValues {
  actualQty: number;
  batchCode?: string;
}

interface OutboundConfirmModalProps {
  loading: boolean;
  onCancel: () => void;
  onConfirm: (payload: ConfirmPickingPayload) => Promise<void>;
  open: boolean;
  task?: OutboundTask;
}

export function OutboundConfirmModal({
  loading,
  onCancel,
  onConfirm,
  open,
  task,
}: OutboundConfirmModalProps): JSX.Element {
  const [form] = Form.useForm<ConfirmFormValues>();
  const { t } = useTranslation();
  const skuQuery = useQuery({
    queryKey: [...PRODUCT_SKUS_QUERY_KEY, task?.productSkuId],
    queryFn: () => getProductSku(task?.productSkuId as number),
    enabled: open && task?.productSkuId !== undefined,
  });
  const trackingMode = skuQuery.data?.batchTrackingMode;

  useEffect(() => {
    if (open) {
      form.setFieldsValue({ actualQty: task?.planQty ?? 1, batchCode: undefined });
    } else {
      form.resetFields();
    }
  }, [form, open, task?.id, task?.planQty]);

  const submit = async (values: ConfirmFormValues): Promise<void> => {
    if (!task || !trackingMode) {
      return;
    }

    const payload: ConfirmPickingPayload = trackingMode === 'LOCATION_VISUAL'
      ? {
        actualQty: values.actualQty,
        locationId: task.locationId,
        skuCode: skuQuery.data?.skuCode,
      }
      : {
        actualQty: values.actualQty,
        batchCode: values.batchCode?.trim(),
      };
    await onConfirm(payload);
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      destroyOnHidden
      forceRender
      okButtonProps={{ disabled: !trackingMode || skuQuery.isError, loading }}
      okText={t('outbound.actions.confirm')}
      onCancel={onCancel}
      onOk={() => form.submit()}
      open={open}
      title={t('outbound.confirm.title', { id: task?.id ?? '-' })}
    >
      <Spin spinning={skuQuery.isLoading}>
        <Descriptions
          column={2}
          items={[
            { key: 'order', label: t('outbound.fields.salesOrderNo'), children: task?.salesOrderNo ?? '-' },
            { key: 'product', label: t('outbound.fields.product'), children: task?.productName ?? '-' },
            { key: 'location', label: t('outbound.fields.location'), children: task?.locationCode ?? '-' },
            { key: 'planned', label: t('outbound.fields.planQty'), children: task?.planQty ?? 0 },
          ]}
          size="small"
        />

        {skuQuery.isError ? (
          <Alert message={t('outbound.confirm.skuLoadFailed')} showIcon type="error" />
        ) : trackingMode === 'LOCATION_VISUAL' ? (
          <Alert message={t('outbound.confirm.locationVisualNotice')} showIcon type="info" />
        ) : trackingMode === 'PRINTED_LABEL' ? (
          <Alert message={t('outbound.confirm.printedLabelNotice')} showIcon type="warning" />
        ) : null}

        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item
            label={t('outbound.fields.actualQty')}
            name="actualQty"
            rules={[
              { required: true, message: t('outbound.validation.quantityRequired') },
              { type: 'number', min: 1, max: task?.planQty ?? 1, message: t('outbound.validation.quantityRange', { max: task?.planQty ?? 1 }) },
            ]}
          >
            <InputNumber min={1} max={task?.planQty ?? 1} precision={0} style={{ width: '100%' }} />
          </Form.Item>

          {trackingMode === 'PRINTED_LABEL' ? (
            <Form.Item
              label={t('outbound.fields.scannedBatchCode')}
              name="batchCode"
              rules={[
                { required: true, whitespace: true, message: t('outbound.validation.batchCodeRequired') },
                {
                  validator: async (_, value: string | undefined) => {
                    if (value?.trim() && value.trim() !== task?.batchCode) {
                      throw new Error(t('outbound.validation.batchCodeMismatch'));
                    }
                  },
                },
              ]}
            >
              <Input autoComplete="off" placeholder={t('outbound.confirm.scanBatchPlaceholder')} />
            </Form.Item>
          ) : null}
        </Form>
      </Spin>
    </Modal>
  );
}
