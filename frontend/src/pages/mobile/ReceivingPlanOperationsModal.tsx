import { Form, Input, InputNumber, Modal } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { ReceivingPlan, ReceivingPlanOperations } from '../../api/receiving';

interface ReceivingPlanOperationsModalProps {
  loading: boolean;
  onCancel: () => void;
  onSubmit: (planId: number, values: ReceivingPlanOperations) => Promise<void> | void;
  open: boolean;
  plan?: ReceivingPlan;
}

type OperationsFormValues = Omit<ReceivingPlanOperations, 'version'>;

function optionalText(value: string | undefined): string | undefined {
  const normalized = value?.trim();
  return normalized || undefined;
}

export function ReceivingPlanOperationsModal({
  loading,
  onCancel,
  onSubmit,
  open,
  plan,
}: ReceivingPlanOperationsModalProps): JSX.Element {
  const [form] = Form.useForm<OperationsFormValues>();
  const { t } = useTranslation();

  useEffect(() => {
    if (!open || !plan) return;
    form.resetFields();
    form.setFieldsValue({
      priority: plan.priority,
      expectedWindowStart: plan.expectedWindowStart,
      expectedWindowEnd: plan.expectedWindowEnd,
      assignedReceiverId: plan.assignedReceiverId,
      assignedReceiverName: plan.assignedReceiverName,
      assignedTeam: plan.assignedTeam,
      dockArea: plan.dockArea,
      workArea: plan.workArea,
      warehouseNotes: plan.warehouseNotes,
    });
  }, [form, open, plan]);

  const submit = async (): Promise<void> => {
    if (!plan) return;
    const values = await form.validateFields();
    await onSubmit(plan.id, {
      version: plan.version,
      priority: values.priority,
      expectedWindowStart: optionalText(values.expectedWindowStart),
      expectedWindowEnd: optionalText(values.expectedWindowEnd),
      assignedReceiverId: values.assignedReceiverId,
      assignedReceiverName: optionalText(values.assignedReceiverName),
      assignedTeam: optionalText(values.assignedTeam),
      dockArea: optionalText(values.dockArea),
      workArea: optionalText(values.workArea),
      warehouseNotes: optionalText(values.warehouseNotes),
    });
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      destroyOnHidden
      okText={t('common.save')}
      onCancel={onCancel}
      onOk={() => void submit()}
      open={open}
      title={t('mobile.receiving.planEditor.title', { planNumber: plan?.planNumber ?? '-' })}
    >
      <p className="receiving-plan-editor-context">
        {t('mobile.receiving.planEditor.context', {
          purchaseOrderNumber: plan?.purchaseOrderNumber ?? '-',
          warehouse: plan?.warehouseName ?? plan?.warehouseCode ?? '-',
          version: plan?.version ?? '-',
        })}
      </p>
      <Form className="receiving-plan-editor" form={form} layout="vertical">
        <div className="receiving-plan-editor-grid">
          <Form.Item label={t('mobile.receiving.plan.priority')} name="priority">
            <InputNumber min={0} precision={0} />
          </Form.Item>
          <Form.Item label={t('mobile.receiving.plan.receiverId')} name="assignedReceiverId">
            <InputNumber min={1} precision={0} />
          </Form.Item>
          <Form.Item label={t('mobile.receiving.plan.windowStart')} name="expectedWindowStart">
            <Input type="datetime-local" />
          </Form.Item>
          <Form.Item label={t('mobile.receiving.plan.windowEnd')} name="expectedWindowEnd">
            <Input type="datetime-local" />
          </Form.Item>
          <Form.Item label={t('mobile.receiving.plan.receiver')} name="assignedReceiverName">
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item label={t('mobile.receiving.plan.team')} name="assignedTeam">
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item label={t('mobile.receiving.plan.dock')} name="dockArea">
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item label={t('mobile.receiving.plan.workArea')} name="workArea">
            <Input maxLength={100} />
          </Form.Item>
        </div>
        <Form.Item label={t('mobile.receiving.plan.notes')} name="warehouseNotes">
          <Input.TextArea maxLength={500} rows={3} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
