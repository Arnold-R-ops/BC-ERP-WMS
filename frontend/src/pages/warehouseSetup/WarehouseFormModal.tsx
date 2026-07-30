import { Form, Input, Modal } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { Warehouse } from '../../api/warehouseSetup';

export interface WarehouseFormValues {
  code: string;
  name: string;
  address?: string;
  contact?: string;
  phone?: string;
}

interface WarehouseFormModalProps {
  warehouse?: Warehouse;
  open: boolean;
  loading: boolean;
  onClose: () => void;
  onSubmit: (values: WarehouseFormValues) => Promise<boolean>;
}

export function WarehouseFormModal({
  warehouse,
  open,
  loading,
  onClose,
  onSubmit,
}: WarehouseFormModalProps): JSX.Element {
  const [form] = Form.useForm<WarehouseFormValues>();
  const { t } = useTranslation();

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({
      code: warehouse?.code ?? '',
      name: warehouse?.name ?? '',
      address: warehouse?.address,
      contact: warehouse?.contact,
      phone: warehouse?.phone,
    });
  }, [form, open, warehouse]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    if (await onSubmit(values)) form.resetFields();
  };

  return (
    <Modal
      confirmLoading={loading}
      onCancel={onClose}
      onOk={() => void submit()}
      open={open}
      title={t(warehouse ? 'warehouseSetup.warehouses.form.editTitle' : 'warehouseSetup.warehouses.form.createTitle')}
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          extra={warehouse ? t('warehouseSetup.warehouses.form.codeImmutable') : undefined}
          label={t('warehouseSetup.warehouses.fields.code')}
          name="code"
          rules={[
            { required: true, message: t('warehouseSetup.warehouses.validation.codeRequired') },
            { pattern: /^[A-Z][A-Z0-9-]{1,19}$/, message: t('warehouseSetup.warehouses.validation.codeInvalid') },
          ]}
        >
          <Input disabled={warehouse !== undefined} maxLength={20} placeholder="WH-GZ-01" />
        </Form.Item>
        <Form.Item label={t('warehouseSetup.warehouses.fields.name')} name="name" rules={[{ required: true, message: t('warehouseSetup.warehouses.validation.nameRequired') }]}>
          <Input maxLength={100} />
        </Form.Item>
        <Form.Item label={t('warehouseSetup.warehouses.fields.address')} name="address">
          <Input maxLength={255} />
        </Form.Item>
        <Form.Item label={t('warehouseSetup.warehouses.fields.contact')} name="contact">
          <Input maxLength={50} />
        </Form.Item>
        <Form.Item label={t('warehouseSetup.warehouses.fields.phone')} name="phone">
          <Input maxLength={20} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
