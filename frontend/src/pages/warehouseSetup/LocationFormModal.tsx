import { Form, Input, InputNumber, Modal, Select } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { Location, LocationZone, Warehouse } from '../../api/warehouseSetup';

export interface LocationFormValues {
  warehouseId: number;
  zone: LocationZone;
  shelfNumber: string;
  positionNumber: string;
  posX?: number;
  posY?: number;
  remark?: string;
}

interface LocationFormModalProps {
  location?: Location;
  warehouses: Warehouse[];
  defaultWarehouseId?: number;
  open: boolean;
  loading: boolean;
  onClose: () => void;
  onSubmit: (values: LocationFormValues) => Promise<boolean>;
}

const ZONES: LocationZone[] = ['ZONE_A', 'ZONE_B', 'ZONE_C', 'ZONE_D', 'ZONE_E', 'ZONE_Q', 'ZONE_R'];

export function LocationFormModal({
  location,
  warehouses,
  defaultWarehouseId,
  open,
  loading,
  onClose,
  onSubmit,
}: LocationFormModalProps): JSX.Element {
  const [form] = Form.useForm<LocationFormValues>();
  const { t } = useTranslation();

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({
      warehouseId: location?.warehouseId ?? defaultWarehouseId,
      zone: location?.zone,
      shelfNumber: location?.shelfNumber ?? '',
      positionNumber: location?.positionNumber ?? '',
      posX: location?.posX ?? 0,
      posY: location?.posY ?? 0,
      remark: location?.remark,
    });
  }, [defaultWarehouseId, form, location, open]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    if (await onSubmit(values)) form.resetFields();
  };

  const identityLocked = location !== undefined;

  return (
    <Modal
      confirmLoading={loading}
      onCancel={onClose}
      onOk={() => void submit()}
      open={open}
      title={t(location ? 'warehouseSetup.locations.form.editTitle' : 'warehouseSetup.locations.form.createTitle')}
      width={620}
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item label={t('warehouseSetup.locations.fields.warehouse')} name="warehouseId" rules={[{ required: true }]}>
          <Select
            disabled={identityLocked}
            options={warehouses.map((warehouse) => ({
              disabled: warehouse.isActive === false,
              label: `${warehouse.code ?? '-'} · ${warehouse.name ?? '-'}`,
              value: warehouse.id,
            }))}
          />
        </Form.Item>
        <Form.Item label={t('warehouseSetup.locations.fields.zone')} name="zone" rules={[{ required: true }]}>
          <Select disabled={identityLocked} options={ZONES.map((zone) => ({ label: t(`warehouseSetup.locations.zones.${zone}`), value: zone }))} />
        </Form.Item>
        <div className="form-grid-two">
          <Form.Item label={t('warehouseSetup.locations.fields.shelfNumber')} name="shelfNumber" rules={[{ required: true }]}>
            <Input disabled={identityLocked} maxLength={20} />
          </Form.Item>
          <Form.Item label={t('warehouseSetup.locations.fields.positionNumber')} name="positionNumber" rules={[{ required: true }]}>
            <Input disabled={identityLocked} maxLength={10} />
          </Form.Item>
        </div>
        {identityLocked ? <p className="form-help-text">{t('warehouseSetup.locations.form.identityImmutable')}</p> : null}
        <div className="form-grid-two">
          <Form.Item label={t('warehouseSetup.locations.fields.posX')} name="posX" rules={[{ type: 'number', min: 0 }]}>
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label={t('warehouseSetup.locations.fields.posY')} name="posY" rules={[{ type: 'number', min: 0 }]}>
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </div>
        <p className="form-help-text">{t('warehouseSetup.locations.form.coordinateHint')}</p>
        <Form.Item label={t('common.remark')} name="remark">
          <Input.TextArea maxLength={500} rows={3} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
