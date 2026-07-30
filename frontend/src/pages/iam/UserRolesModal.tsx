import { Alert, Form, Modal, Select } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { Role, UserAccount } from '../../api/iam';
import { findDefaultRoleId, roleLabel } from './iamUtils';

export interface UserRoleValues {
  roleIds: number[];
  defaultRoleId: number;
}

interface UserRolesModalProps {
  loading: boolean;
  open: boolean;
  roles: Role[];
  user?: UserAccount;
  onClose: () => void;
  onSubmit: (values: UserRoleValues) => Promise<boolean>;
}

export function UserRolesModal({ loading, open, roles, user, onClose, onSubmit }: UserRolesModalProps): JSX.Element {
  const [form] = Form.useForm<UserRoleValues>();
  const { t } = useTranslation();
  const selectedRoleIds = Form.useWatch('roleIds', form) ?? [];
  const activeRoles = roles.filter((role) => role.status === 'ACTIVE');
  const selectedRoles = activeRoles.filter((role) => role.id !== undefined && selectedRoleIds.includes(role.id));

  useEffect(() => {
    if (!open || !user) return;
    form.setFieldsValue({
      roleIds: user.roleIds ?? [],
      defaultRoleId: findDefaultRoleId(user, roles),
    });
  }, [form, open, roles, user]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    if (await onSubmit(values)) onClose();
  };

  return (
    <Modal
      confirmLoading={loading}
      destroyOnHidden
      maskClosable={false}
      onCancel={onClose}
      onOk={() => void submit()}
      open={open}
      title={t('iam.users.roles.title', { username: user?.username ?? '' })}
      width={640}
    >
      <Alert
        className="form-notice"
        description={t('iam.users.roles.replaceNotice')}
        showIcon
        type="warning"
      />
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          label={t('iam.users.fields.roles')}
          name="roleIds"
          rules={[{ required: true, message: t('iam.users.validation.roleRequired') }]}
        >
          <Select
            mode="multiple"
            optionFilterProp="label"
            options={activeRoles.map((role) => ({ label: roleLabel(role), value: role.id }))}
            onChange={(ids: number[]) => {
              const currentDefault = form.getFieldValue('defaultRoleId');
              if (!ids.includes(currentDefault)) form.setFieldValue('defaultRoleId', ids[0]);
            }}
          />
        </Form.Item>
        <Form.Item
          label={t('iam.users.fields.defaultRole')}
          name="defaultRoleId"
          rules={[{ required: true, message: t('iam.users.validation.defaultRoleRequired') }]}
        >
          <Select options={selectedRoles.map((role) => ({ label: roleLabel(role), value: role.id }))} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
