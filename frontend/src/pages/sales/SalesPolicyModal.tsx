import { ModalForm, ProFormDigit, ProFormSelect, ProFormSwitch, ProFormTextArea } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntdApp, Spin } from 'antd';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  getSalesOrderPolicy,
  SALES_POLICY_QUERY_KEY,
  updateSalesOrderPolicy,
  type UpdateSalesOrderPolicyPayload,
} from '../../api/sales';

interface SalesPolicyModalProps {
  onClose: () => void;
  onSaved: () => Promise<void>;
  open: boolean;
}

export function SalesPolicyModal({ onClose, onSaved, open }: SalesPolicyModalProps): JSX.Element {
  const { t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const policyQuery = useQuery({
    queryKey: SALES_POLICY_QUERY_KEY,
    queryFn: getSalesOrderPolicy,
    enabled: open,
  });
  const mutation = useMutation({ mutationFn: updateSalesOrderPolicy });

  const save = async (payload: UpdateSalesOrderPolicyPayload): Promise<boolean> => {
    try {
      await mutation.mutateAsync(payload);
      await queryClient.invalidateQueries({ queryKey: SALES_POLICY_QUERY_KEY });
      message.success(t('sales.policy.messages.saved'));
      onClose();
      await onSaved();
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const policy = policyQuery.data;
  return (
    <ModalForm<UpdateSalesOrderPolicyPayload>
      key={`${policy?.approvalMode ?? 'loading'}-${String(policy?.detailedLogisticsEnabled ?? false)}-${open}`}
      initialValues={{
        approvalMode: policy?.approvalMode ?? 'DIRECT',
        approvalAmountThreshold: policy?.approvalAmountThreshold ?? 50000,
        detailedLogisticsEnabled: policy?.detailedLogisticsEnabled ?? false,
        reason: '',
      }}
      modalProps={{ destroyOnHidden: true, maskClosable: false }}
      onFinish={save}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{ submitButtonProps: { disabled: policyQuery.isLoading, loading: mutation.isPending } }}
      title={t('sales.policy.title')}
      width={620}
    >
      <Spin spinning={policyQuery.isLoading}>
        <Alert message={t('sales.policy.notice')} showIcon type="info" />
        <ProFormSelect
          label={t('sales.policy.approvalMode')}
          name="approvalMode"
          options={['DIRECT', 'RISK_BASED', 'ALWAYS'].map((value) => ({
            label: t(`sales.approvalModes.${value}`), value,
          }))}
          rules={[{ required: true }]}
        />
        <ProFormDigit
          fieldProps={{ min: 0, precision: 2 }}
          label={t('sales.policy.amountThreshold')}
          name="approvalAmountThreshold"
          rules={[{ required: true }]}
        />
        <ProFormSwitch label={t('sales.policy.detailedLogistics')} name="detailedLogisticsEnabled" />
        <ProFormTextArea
          fieldProps={{ maxLength: 500, showCount: true }}
          label={t('sales.policy.reason')}
          name="reason"
          rules={[{ required: true, min: 2, message: t('sales.policy.reasonRequired') }]}
        />
      </Spin>
    </ModalForm>
  );
}
