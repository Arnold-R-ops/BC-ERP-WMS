import { Alert, Form, Input, Modal, Select, Space, Tag } from 'antd';
import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import type { AssignableWarehouse, Permission, Role, UserAccount } from '../../api/iam';
import { roleLabel } from './iamUtils';
import { isRequestableRole, roleHighRiskPermissionCount, type PermissionRequestFormValues } from './permissionRequestModel';

interface Props {
  loading: boolean;
  onClose: () => void;
  onSubmit: (values: PermissionRequestFormValues) => Promise<boolean>;
  open: boolean;
  permissions: Permission[];
  roles: Role[];
  users: UserAccount[];
  warehouses: AssignableWarehouse[];
}

export function PermissionRequestModal({
  loading,
  onClose,
  onSubmit,
  open,
  permissions,
  roles,
  users,
  warehouses,
}: Props): JSX.Element {
  const { t } = useTranslation();
  const [form] = Form.useForm<PermissionRequestFormValues>();
  const requestedRoleId = Form.useWatch('requestedRoleId', form);
  const requestableRoles = useMemo(() => roles.filter(isRequestableRole), [roles]);
  const selectedRole = requestableRoles.find((role) => role.id === requestedRoleId);
  const highRiskPermissionCount = selectedRole ? roleHighRiskPermissionCount(selectedRole, permissions) : 0;
  const requiresWarehouses = selectedRole?.roleCode === 'WAREHOUSE_STAFF';

  useEffect(() => {
    if (open) form.resetFields();
  }, [form, open]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields().catch(() => undefined);
    if (values) await onSubmit(values);
  };

  return (
    <Modal
      confirmLoading={loading}
      destroyOnHidden
      maskClosable={false}
      okText={t('iam.permissionRequests.actions.submit')}
      onCancel={onClose}
      onOk={() => void submit()}
      open={open}
      title={t('iam.permissionRequests.form.title')}
      width={680}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert description={t('iam.permissionRequests.liveNotice')} message={t('iam.permissionRequests.liveTitle')} showIcon type="warning" />
        <Alert description={t('iam.permissionRequests.form.policyNotice')} showIcon type="info" />
        <Form form={form} layout="vertical">
          <Form.Item label={t('iam.permissionRequests.fields.targetUser')} name="targetUserId" rules={[{ required: true, message: t('iam.permissionRequests.validation.targetRequired') }]}>
            <Select
              optionFilterProp="label"
              options={users.map((user) => ({
                label: `${user.username ?? '-'}${user.displayName ? ` · ${user.displayName}` : ''}`,
                value: user.id,
              }))}
              showSearch
            />
          </Form.Item>
          <Form.Item label={t('iam.permissionRequests.fields.requestedPackage')} name="requestedRoleId" rules={[{ required: true, message: t('iam.permissionRequests.validation.packageRequired') }]}>
            <Select
              optionFilterProp="label"
              options={requestableRoles.flatMap((role) => role.id === undefined ? [] : [{ label: roleLabel(role), value: role.id }])}
              showSearch
            />
          </Form.Item>
          {selectedRole ? (
            <Alert
              description={highRiskPermissionCount
                ? t('iam.permissionRequests.highRiskNotice', { count: highRiskPermissionCount })
                : t('iam.permissionRequests.normalRiskNotice')}
              message={<>{roleLabel(selectedRole)} <Tag color={highRiskPermissionCount ? 'red' : 'default'}>{t('iam.permissionRequests.highRiskCount', { count: highRiskPermissionCount })}</Tag></>}
              showIcon
              type={highRiskPermissionCount ? 'warning' : 'info'}
            />
          ) : null}
          {requiresWarehouses ? (
            <Form.Item label={t('iam.permissionRequests.fields.warehouses')} name="warehouseIds" rules={[{ required: true, message: t('iam.permissionRequests.validation.warehouseRequired') }]}>
              <Select
                mode="multiple"
                optionFilterProp="label"
                options={warehouses.map((warehouse) => ({ label: `${warehouse.code ?? '-'} · ${warehouse.name ?? '-'}`, value: warehouse.id }))}
              />
            </Form.Item>
          ) : null}
          <Form.Item label={t('iam.permissionRequests.fields.reason')} name="requestReason" rules={[{ max: 500 }]}>
            <Input.TextArea maxLength={500} rows={3} showCount />
          </Form.Item>
        </Form>
      </Space>
    </Modal>
  );
}
