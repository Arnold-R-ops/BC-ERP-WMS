import { Alert, Form, Input, Modal, Select } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type {
  PurchaseApprovalMode,
  PurchaseGovernancePolicy,
  PurchaseGovernancePolicyUpdatePayload,
  ShortReceiptClosureMode,
} from '../../api/purchasing';

interface PurchaseGovernancePolicyModalProps {
  loading: boolean;
  onCancel: () => void;
  onConfirm: (payload: PurchaseGovernancePolicyUpdatePayload) => Promise<void> | void;
  open: boolean;
  policy?: PurchaseGovernancePolicy;
}

interface PolicyFormValues {
  purchaseApprovalMode: PurchaseApprovalMode;
  shortReceiptClosureMode: ShortReceiptClosureMode;
  reason: string;
}

export function PurchaseGovernancePolicyModal({
  loading,
  onCancel,
  onConfirm,
  open,
  policy,
}: PurchaseGovernancePolicyModalProps): JSX.Element {
  const [form] = Form.useForm<PolicyFormValues>();
  const { t } = useTranslation();

  useEffect(() => {
    if (!open || !policy) return;
    form.setFieldsValue({
      purchaseApprovalMode: policy.configuredPurchaseApprovalMode,
      shortReceiptClosureMode: policy.configuredShortReceiptClosureMode,
      reason: '',
    });
  }, [form, open, policy]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    await onConfirm({ ...values, confirmed: true });
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      destroyOnHidden
      okText={t('common.confirm')}
      onCancel={onCancel}
      onOk={() => void submit()}
      open={open}
      title={t('purchasing.governance.title')}
    >
      <Alert
        message={policy?.approvalEntitled
          ? t('purchasing.governance.entitledNotice')
          : t('purchasing.governance.ordinaryNotice')}
        showIcon
        type={policy?.approvalEntitled ? 'info' : 'warning'}
      />
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item
          label={t('purchasing.governance.purchaseApproval')}
          name="purchaseApprovalMode"
          rules={[{ required: true }]}
        >
          <Select
            options={(policy?.availablePurchaseApprovalModes ?? ['NONE']).map((value) => ({
              label: t(`purchasing.governance.purchaseModes.${value}`), value,
            }))}
          />
        </Form.Item>
        <Form.Item
          label={t('purchasing.governance.shortReceiptClosure')}
          name="shortReceiptClosureMode"
          rules={[{ required: true }]}
        >
          <Select
            options={(policy?.availableShortReceiptClosureModes ?? ['DIRECT_WITH_REASON']).map((value) => ({
              label: t(`purchasing.governance.closureModes.${value}`), value,
            }))}
          />
        </Form.Item>
        <Form.Item
          label={t('purchasing.governance.changeReason')}
          name="reason"
          rules={[{ required: true, message: t('purchasing.governance.reasonRequired') }]}
        >
          <Input.TextArea maxLength={500} rows={3} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
