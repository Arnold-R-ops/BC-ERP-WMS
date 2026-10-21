import { Checkbox, Descriptions, Form, Input, InputNumber, Modal } from 'antd';
import { useEffect, useState } from 'react';
import { PackagingEditor } from '../inventory/PackagingEditor';
import { PackagingSurface } from '../inventory/PackagingSurface';
import { emptyPackaging, packagingTotal, type StockPackaging } from '../../api/packaging';
import { useTranslation } from 'react-i18next';
import type { StocktakeItem, SubmitCountPayload } from '../../api/stocktake';

interface StocktakeCountModalProps {
  item?: StocktakeItem;
  loading: boolean;
  onCancel: () => void;
  onConfirm: (payload: SubmitCountPayload) => Promise<void>;
  open: boolean;
}

export function StocktakeCountModal({
  item,
  loading,
  onCancel,
  onConfirm,
  open,
}: StocktakeCountModalProps): JSX.Element {
  const [form] = Form.useForm<SubmitCountPayload>();
  const { t } = useTranslation();
  const [recordPackaging, setRecordPackaging] = useState(false);
  const [packaging, setPackaging] = useState<StockPackaging>(emptyPackaging(1));

  useEffect(() => {
    if (open) {
      form.setFieldsValue({ countedQty: item?.countedQty, remark: item?.remark });
      setRecordPackaging(!!item?.packagingRequired || !!item?.countedPackaging || (item?.standardPackSize ?? 1) > 1);
      setPackaging(item?.countedPackaging ?? emptyPackaging(item?.standardPackSize ?? 1));
    }
    else form.resetFields();
  }, [form, item?.countedQty, item?.id, item?.remark, open]);

  return (
    <PackagingSurface><Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      destroyOnHidden
      okText={t('stocktake.actions.saveCount')}
      onCancel={onCancel}
      onOk={() => form.submit()}
      open={open}
      title={t('stocktake.count.title')}
    >
      <Descriptions
        column={1}
        items={[
          { key: 'product', label: t('stocktake.fields.product'), children: item?.productName ?? '-' },
          { key: 'batch', label: t('stocktake.fields.batchCode'), children: item?.batchCode ?? '-' },
          { key: 'location', label: t('stocktake.fields.location'), children: item?.locationCode ?? '-' },
        ]}
        size="small"
      />
      <p className="modal-description">{t('stocktake.count.blindNotice')}</p>
      <Form form={form} layout="vertical" onFinish={values => onConfirm({ ...values, packaging: recordPackaging ? packaging : undefined })}>
        <Checkbox checked={recordPackaging} disabled={item?.packagingRequired} onChange={e => setRecordPackaging(e.target.checked)}>{t('packaging.record')}</Checkbox>
        {recordPackaging && <PackagingEditor value={packaging} unit={item?.baseUnit} disabled={loading}
          onChange={value => { setPackaging(value); form.setFieldValue('countedQty', packagingTotal(value)); }} />}
        <Form.Item
          label={t('stocktake.fields.countedQty')}
          name="countedQty"
          rules={[{ required: true, message: t('stocktake.validation.countRequired') }]}
        >
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item label={t('common.remark')} name="remark">
          <Input.TextArea maxLength={500} rows={3} showCount />
        </Form.Item>
      </Form>
    </Modal></PackagingSurface>
  );
}
