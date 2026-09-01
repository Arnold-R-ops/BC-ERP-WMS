import { Alert, Form, Input, Modal, Radio, Switch } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { TransferPolicy, TransferPolicyUpdatePayload } from '../../api/transfers';

interface TransferPolicyModalProps {
  loading: boolean;
  onCancel: () => void;
  onSubmit: (payload: TransferPolicyUpdatePayload) => Promise<void>;
  open: boolean;
  policy?: TransferPolicy;
}

export function TransferPolicyModal({ loading, onCancel, onSubmit, open, policy }: TransferPolicyModalProps): JSX.Element {
  const [form] = Form.useForm<TransferPolicyUpdatePayload>();
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-US';

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({
      approvalMode: policy?.approvalMode ?? 'DIRECT',
      receivingInspectionEnabled: policy?.receivingInspectionEnabled ?? false,
      reason: '',
    });
  }, [form, open, policy]);

  return (
    <Modal
      confirmLoading={loading}
      onCancel={onCancel}
      onOk={() => void form.validateFields().then(onSubmit)}
      open={open}
      title={english ? 'Transfer policy' : '调拨策略'}
    >
      <Alert
        message={english ? 'Approval and receiving inspection are independent tenant options.' : '调拨审批和调入质检是两个相互独立的租户开关。'}
        showIcon
        style={{ marginBottom: 16 }}
        type="info"
      />
      <Form form={form} layout="vertical">
        <Form.Item label={english ? 'Approval mode' : '审批模式'} name="approvalMode" rules={[{ required: true }]}>
          <Radio.Group options={[
            { label: english ? 'Direct approval (default)' : '直接批准（默认）', value: 'DIRECT' },
            { label: english ? 'Always require approval' : '全部需要审批', value: 'ALWAYS' },
          ]} />
        </Form.Item>
        <Form.Item
          label={english ? 'Inspect intact stock at destination' : '目标仓完好商品进入待检'}
          name="receivingInspectionEnabled"
          valuePropName="checked"
        >
          <Switch />
        </Form.Item>
        <Form.Item label={english ? 'Change reason' : '修改原因'} name="reason" rules={[{ required: true }]}>
          <Input.TextArea maxLength={500} rows={3} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
