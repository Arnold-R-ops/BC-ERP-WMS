import { CheckOutlined, CloseOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { Alert, Button, Descriptions, Form, Input, Modal, Space, Table, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Permission, Role, RoleGovernanceAudit, RolePackageDraftRequest } from '../../api/iam';
import { formatDateTime } from '../workflowUtils';
import { roleLabel } from './iamUtils';

export type RoleGovernanceMode = 'EDIT' | 'SUBMIT' | 'REVIEW' | 'ACTIVATE' | 'DEACTIVATE' | 'HISTORY';

interface Props {
  canApproveHighRisk?: boolean;
  directPermissions: Permission[];
  history: RoleGovernanceAudit[];
  loading: boolean;
  mode?: RoleGovernanceMode;
  onClose: () => void;
  onReview: (approved: boolean, comment?: string) => Promise<boolean>;
  onRuntimeChange: (reason: string, confirmationCode: string) => Promise<boolean>;
  onSaveDraft: (payload: RolePackageDraftRequest) => Promise<boolean>;
  onSubmitReview: (reason?: string) => Promise<boolean>;
  permissionCatalog: Permission[];
  role?: Role;
  submitting: boolean;
}

interface DraftValues {
  roleName: string;
  description: string;
  reason?: string;
}

interface CommentValues {
  comment?: string;
}

interface RuntimeValues {
  reason: string;
  confirmationCode: string;
}

export function RoleGovernanceModal({
  canApproveHighRisk = true,
  directPermissions,
  history,
  loading,
  mode,
  onClose,
  onReview,
  onRuntimeChange,
  onSaveDraft,
  onSubmitReview,
  permissionCatalog,
  role,
  submitting,
}: Props): JSX.Element {
  const { i18n, t } = useTranslation();
  const [draftForm] = Form.useForm<DraftValues>();
  const [commentForm] = Form.useForm<CommentValues>();
  const [runtimeForm] = Form.useForm<RuntimeValues>();
  const [selectedPermissionIds, setSelectedPermissionIds] = useState<number[]>([]);

  const assignablePermissions = useMemo(() => permissionCatalog.filter((permission) => (
    permission.id !== undefined
    && permission.status === 'ACTIVE'
    && permission.customAssignable === true
    && permission.riskLevel !== 'CRITICAL'
  )), [permissionCatalog]);
  const selectedPermissions = useMemo(() => assignablePermissions.filter((permission) => (
    permission.id !== undefined && selectedPermissionIds.includes(permission.id)
  )), [assignablePermissions, selectedPermissionIds]);
  const highRiskCount = selectedPermissions.filter((permission) => permission.riskLevel === 'HIGH').length;
  const currentHighRiskCount = directPermissions.filter((permission) => permission.riskLevel === 'HIGH').length;
  const highRiskApprovalBlocked = mode === 'REVIEW' && currentHighRiskCount > 0 && !canApproveHighRisk;

  useEffect(() => {
    if (!role || !mode) return;
    draftForm.setFieldsValue({
      roleName: role.roleName ?? '',
      description: role.description ?? '',
      reason: undefined,
    });
    commentForm.resetFields();
    runtimeForm.resetFields();
    setSelectedPermissionIds(directPermissions.flatMap((permission) => permission.id === undefined ? [] : [permission.id]));
  }, [commentForm, directPermissions, draftForm, mode, role, runtimeForm]);

  const permissionColumns = [
    {
      title: t('iam.permissions.fields.permission'), dataIndex: 'permissionName',
      render: (value: string | undefined, permission: Permission) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{value || permission.permissionCode || '-'}</Typography.Text>
          <Typography.Text code copyable>{permission.permissionCode}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t('iam.permissions.fields.type'), dataIndex: 'permissionType', width: 90,
      render: (value: string | undefined) => <Tag>{value ?? '-'}</Tag>,
    },
    {
      title: t('iam.permissions.fields.risk'), dataIndex: 'riskLevel', width: 100,
      render: (value: string | undefined) => (
        <Tag color={value === 'HIGH' ? 'red' : 'default'}>{t(`iam.permissions.risks.${value ?? 'NORMAL'}`)}</Tag>
      ),
    },
  ];

  const historyColumns = [
    {
      title: t('iam.roles.governance.history.time'), dataIndex: 'createdAt', width: 180,
      render: (value: string | undefined) => formatDateTime(value, i18n.language),
    },
    {
      title: t('iam.roles.governance.history.action'), dataIndex: 'action', width: 130,
      render: (value: string | undefined) => <Tag>{t(`iam.roles.governance.actions.${value ?? 'UPDATE_DRAFT'}`)}</Tag>,
    },
    { title: t('iam.roles.governance.history.operator'), dataIndex: 'operatorUsername', width: 150 },
    {
      title: t('iam.roles.governance.history.risk'), dataIndex: 'highRiskCount', width: 100,
      render: (value: number | undefined) => <Tag color={value ? 'red' : 'default'}>{value ?? 0}</Tag>,
    },
    { title: t('iam.roles.governance.history.reason'), dataIndex: 'reason', render: (value: string | undefined) => value || '-' },
  ];

  const title = mode ? t(`iam.roles.governance.titles.${mode}`, { role: roleLabel(role ?? {}) }) : '';

  const submitDraft = async (): Promise<void> => {
    const values = await draftForm.validateFields().catch(() => undefined);
    if (!values) return;
    await onSaveDraft({ ...values, permissionIds: selectedPermissionIds });
  };
  const submitReview = async (): Promise<void> => {
    const values = await commentForm.validateFields().catch(() => undefined);
    if (!values) return;
    await onSubmitReview(values.comment);
  };
  const review = async (approved: boolean): Promise<void> => {
    const values = await commentForm.validateFields().catch(() => undefined);
    if (!values) return;
    if (!approved && !values.comment?.trim()) {
      commentForm.setFields([{ name: 'comment', errors: [t('iam.roles.governance.rejectionCommentRequired')] }]);
      return;
    }
    await onReview(approved, values.comment);
  };
  const changeRuntime = async (): Promise<void> => {
    const values = await runtimeForm.validateFields().catch(() => undefined);
    if (!values) return;
    await onRuntimeChange(values.reason, values.confirmationCode);
  };

  const footer = mode === 'EDIT' ? [
    <Button key="cancel" onClick={onClose}>{t('common.cancel')}</Button>,
    <Button key="save" loading={submitting} onClick={() => void submitDraft()} type="primary">{t('common.save')}</Button>,
  ] : mode === 'SUBMIT' ? [
    <Button key="cancel" onClick={onClose}>{t('common.cancel')}</Button>,
    <Button key="submit" loading={submitting} onClick={() => void submitReview()} type="primary">{t('iam.roles.governance.submit')}</Button>,
  ] : mode === 'REVIEW' ? [
    <Button danger icon={<CloseOutlined />} key="reject" loading={submitting} onClick={() => void review(false)}>{t('iam.roles.governance.reject')}</Button>,
    <Button disabled={highRiskApprovalBlocked} icon={<CheckOutlined />} key="approve" loading={submitting} onClick={() => void review(true)} type="primary">{t('iam.roles.governance.approve')}</Button>,
  ] : mode === 'ACTIVATE' || mode === 'DEACTIVATE' ? [
    <Button key="cancel" onClick={onClose}>{t('common.cancel')}</Button>,
    <Button danger={mode === 'DEACTIVATE'} key="confirm" loading={submitting} onClick={() => void changeRuntime()} type="primary">
      {t(`iam.roles.governance.${mode === 'ACTIVATE' ? 'activate' : 'deactivate'}`)}
    </Button>,
  ] : [<Button key="close" onClick={onClose}>{t('common.close')}</Button>];

  return (
    <Modal
      destroyOnHidden
      footer={footer}
      maskClosable={false}
      onCancel={onClose}
      open={Boolean(role && mode)}
      title={<Space><SafetyCertificateOutlined />{title}</Space>}
      width={mode === 'EDIT' || mode === 'HISTORY' ? 900 : 760}
    >
      {mode === 'EDIT' && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Alert message={t('iam.roles.governance.editNotice')} showIcon type="info" />
          <Form form={draftForm} layout="vertical">
            <Form.Item label={t('iam.roles.fields.name')} name="roleName" rules={[{ required: true }, { max: 100 }]}>
              <Input maxLength={100} />
            </Form.Item>
            <Form.Item label={t('iam.roles.fields.description')} name="description" rules={[{ required: true }, { max: 500 }]}>
              <Input.TextArea maxLength={500} rows={3} showCount />
            </Form.Item>
            <Form.Item label={t('iam.roles.governance.editReason')} name="reason" rules={[{ max: 500 }]}>
              <Input.TextArea maxLength={500} rows={2} />
            </Form.Item>
          </Form>
          <Alert
            message={t('iam.roles.governance.selectionSummary', { total: selectedPermissionIds.length, high: highRiskCount })}
            showIcon
            type={highRiskCount ? 'warning' : 'success'}
          />
          <Table<Permission>
            columns={permissionColumns}
            dataSource={assignablePermissions}
            loading={loading}
            pagination={false}
            rowKey={(permission) => permission.id as number}
            rowSelection={{
              selectedRowKeys: selectedPermissionIds,
              onChange: (keys) => setSelectedPermissionIds(keys.map(Number)),
            }}
            scroll={{ y: 360 }}
            size="small"
          />
        </Space>
      )}

      {(mode === 'SUBMIT' || mode === 'REVIEW') && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {highRiskApprovalBlocked && (
            <Alert
              description={t('iam.roles.governance.highRiskSuperAdminRequired')}
              showIcon
              type="warning"
            />
          )}
          <Alert
            description={t(currentHighRiskCount ? 'iam.roles.governance.highRiskReviewNotice' : 'iam.roles.governance.normalReviewNotice')}
            message={t('iam.roles.governance.simpleTemplate')}
            showIcon
            type={currentHighRiskCount ? 'warning' : 'info'}
          />
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label={t('iam.roles.fields.code')}>{role?.roleCode}</Descriptions.Item>
            <Descriptions.Item label={t('iam.roles.governance.highRiskCount')}>{currentHighRiskCount}</Descriptions.Item>
            <Descriptions.Item label={t('iam.roles.governance.submitter')}>{role?.reviewSubmittedByUsername || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('iam.roles.fields.directPermissions')}>{directPermissions.length}</Descriptions.Item>
          </Descriptions>
          <Table<Permission>
            columns={permissionColumns}
            dataSource={directPermissions}
            loading={loading}
            pagination={false}
            rowKey={(permission) => String(permission.id ?? permission.permissionCode)}
            scroll={{ y: 280 }}
            size="small"
          />
          <Form form={commentForm} layout="vertical">
            <Form.Item
              label={t(mode === 'REVIEW' ? 'iam.roles.governance.reviewComment' : 'iam.roles.governance.submitReason')}
              name="comment"
              rules={[
                { max: 500 },
                ...(mode === 'SUBMIT' && currentHighRiskCount ? [{ required: true, message: t('iam.roles.governance.highRiskReasonRequired') }] : []),
              ]}
            >
              <Input.TextArea maxLength={500} rows={3} showCount />
            </Form.Item>
          </Form>
        </Space>
      )}

      {(mode === 'ACTIVATE' || mode === 'DEACTIVATE') && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Alert
            description={t(`iam.roles.governance.${mode === 'ACTIVATE' ? 'activateNotice' : 'deactivateNotice'}`)}
            message={t(`iam.roles.governance.${mode === 'ACTIVATE' ? 'activate' : 'deactivate'}`)}
            showIcon
            type={mode === 'ACTIVATE' ? 'warning' : 'error'}
          />
          <Form form={runtimeForm} layout="vertical">
            <Form.Item label={t('iam.roles.governance.operationReason')} name="reason" rules={[{ required: true }, { max: 500 }]}>
              <Input.TextArea maxLength={500} rows={3} showCount />
            </Form.Item>
            <Form.Item
              label={t('iam.roles.governance.confirmationCode', { code: role?.roleCode })}
              name="confirmationCode"
              rules={[
                { required: true },
                { validator: async (_, value) => value === role?.roleCode ? Promise.resolve() : Promise.reject(new Error(t('iam.roles.governance.confirmationMismatch'))) },
              ]}
            >
              <Input autoComplete="off" />
            </Form.Item>
          </Form>
        </Space>
      )}

      {mode === 'HISTORY' && (
        <Table<RoleGovernanceAudit>
          columns={historyColumns}
          dataSource={history}
          loading={loading}
          pagination={{ defaultPageSize: 10 }}
          rowKey={(audit) => String(audit.id)}
          scroll={{ x: 820 }}
          size="small"
        />
      )}
    </Modal>
  );
}
