import { Alert, Form, Input, Modal } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { PermissionRequest } from '../../api/iam';
import type { PermissionRequestDecision } from './permissionRequestModel';

interface Props {
  decision?: PermissionRequestDecision;
  loading: boolean;
  onClose: () => void;
  onConfirm: (comment?: string) => Promise<boolean>;
  request?: PermissionRequest;
}

interface Values { comment?: string; }

export function PermissionRequestDecisionModal({ decision, loading, onClose, onConfirm, request }: Props): JSX.Element {
  const { t } = useTranslation();
  const [form] = Form.useForm<Values>();
  const rejecting = decision === 'REJECTED';
  const revoking = decision === 'REVOKED';
  const commentRequired = rejecting || revoking;

  useEffect(() => {
    if (decision && request) form.resetFields();
  }, [decision, form, request]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields().catch(() => undefined);
    if (values) await onConfirm(values.comment);
  };

  return (
    <Modal
      confirmLoading={loading}
      destroyOnHidden
      maskClosable={false}
      okButtonProps={{ danger: commentRequired }}
      okText={t(revoking
        ? 'iam.permissionRequests.actions.revoke'
        : rejecting ? 'iam.permissionRequests.actions.reject' : 'iam.permissionRequests.actions.approve')}
      onCancel={onClose}
      onOk={() => void submit()}
      open={Boolean(decision && request)}
      title={t(revoking
        ? 'iam.permissionRequests.decision.revokeTitle'
        : rejecting ? 'iam.permissionRequests.decision.rejectTitle' : 'iam.permissionRequests.decision.approveTitle')}
    >
      <Alert
        description={t(revoking
          ? 'iam.permissionRequests.decision.revokeNotice'
          : 'iam.permissionRequests.decision.reviewNotice')}
        message={request?.requestedRoleName ?? request?.requestedRoleCode}
        showIcon
        type={commentRequired ? 'warning' : 'info'}
      />
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item
          label={t('iam.permissionRequests.fields.reviewComment')}
          name="comment"
          rules={[
            { max: 500 },
            ...(commentRequired ? [{ required: true, message: t(revoking
              ? 'iam.permissionRequests.validation.revocationCommentRequired'
              : 'iam.permissionRequests.validation.rejectionCommentRequired') }] : []),
          ]}
        >
          <Input.TextArea maxLength={500} rows={3} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
