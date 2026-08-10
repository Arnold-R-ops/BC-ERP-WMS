import {
  ModalForm,
  ProFormDependency,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { Alert } from 'antd';
import { useTranslation } from 'react-i18next';
import type { AssignableWarehouse, Role, UserAccount } from '../../api/iam';
import { findDefaultRoleId, isRoleAssignable, roleLabel } from './iamUtils';

export interface UserFormValues {
  username?: string;
  password?: string;
  displayName?: string;
  roleIds?: number[];
  warehouseIds?: number[];
  enabled?: boolean;
  defaultRoleId?: number;
  remark?: string;
}

interface UserFormModalProps {
  loading: boolean;
  open: boolean;
  roles: Role[];
  warehouses: AssignableWarehouse[];
  user?: UserAccount;
  isSelf?: boolean;
  onClose: () => void;
  onSubmit: (values: UserFormValues) => Promise<boolean>;
}

export function UserFormModal({
  loading, open, roles, warehouses, user, isSelf = false, onClose, onSubmit,
}: UserFormModalProps): JSX.Element {
  const { t } = useTranslation();
  const editing = user?.id !== undefined;
  const activeRoles = roles.filter(isRoleAssignable);
  const assignedRoles = roles.filter((role) => role.id !== undefined && user?.roleIds?.includes(role.id));
  const warehouseStaffRoleId = roles.find((role) => role.roleCode === 'WAREHOUSE_STAFF')?.id;

  return (
    <ModalForm<UserFormValues>
      key={user?.id ?? 'new-user'}
      initialValues={{
        username: user?.username ?? '',
        displayName: user?.displayName ?? '',
        roleIds: user?.roleIds ?? [],
        warehouseIds: user?.warehouseIds ?? [],
        enabled: user?.enabled ?? true,
        defaultRoleId: user ? findDefaultRoleId(user, roles) : undefined,
        remark: user?.remark ?? '',
      }}
      modalProps={{ destroyOnHidden: true, maskClosable: false }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{
        searchConfig: { resetText: t('common.cancel'), submitText: editing ? t('common.save') : t('common.create') },
        submitButtonProps: { loading },
      }}
      title={t(editing ? 'iam.users.form.editTitle' : 'iam.users.form.createTitle')}
      width={680}
    >
      {editing && (
        <Alert
          className="form-notice"
          description={t('iam.users.form.identityImmutable')}
          showIcon
          type="info"
        />
      )}
      {isSelf && (
        <Alert
          className="form-notice"
          description={t('iam.users.form.selfProtection')}
          showIcon
          type="warning"
        />
      )}
      <ProFormText
        disabled={editing}
        fieldProps={{ autoComplete: 'off', maxLength: 50 }}
        label={t('iam.users.fields.username')}
        name="username"
        rules={editing ? [] : [
          { required: true, message: t('iam.users.validation.usernameRequired') },
          { min: 3, max: 50, message: t('iam.users.validation.usernameLength') },
        ]}
      />
      {!editing && (
        <ProFormText.Password
          fieldProps={{ autoComplete: 'new-password', maxLength: 64 }}
          label={t('iam.users.fields.initialPassword')}
          name="password"
          rules={[
            { required: true, message: t('iam.users.validation.passwordRequired') },
            { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/, message: t('iam.users.validation.passwordPolicy') },
          ]}
        />
      )}
      <ProFormText
        fieldProps={{ maxLength: 100 }}
        label={t('iam.users.fields.displayName')}
        name="displayName"
      />
      {!editing && (
        <>
          <ProFormSelect
            fieldProps={{ mode: 'multiple', optionFilterProp: 'label' }}
            label={t('iam.users.fields.roles')}
            name="roleIds"
            options={activeRoles.map((role) => ({ label: roleLabel(role), value: role.id }))}
            rules={[{ required: true, message: t('iam.users.validation.roleRequired') }]}
          />
          <ProFormDependency name={['roleIds']}>
            {({ roleIds }) => warehouseStaffRoleId !== undefined
              && (roleIds as number[] | undefined)?.includes(warehouseStaffRoleId) ? (
                <ProFormSelect
                  fieldProps={{ mode: 'multiple', optionFilterProp: 'label' }}
                  label={t('iam.users.fields.warehouses')}
                  name="warehouseIds"
                  options={warehouses.map((warehouse) => ({
                    label: `${warehouse.code ?? '-'} · ${warehouse.name ?? '-'}`,
                    value: warehouse.id,
                  }))}
                  rules={[{ required: true, message: t('iam.users.validation.warehouseRequired') }]}
                />
              ) : null}
          </ProFormDependency>
        </>
      )}
      {editing && (
        <ProFormSelect
          label={t('iam.users.fields.defaultRole')}
          name="defaultRoleId"
          options={assignedRoles.map((role) => ({ label: roleLabel(role), value: role.id }))}
          rules={[{ required: true, message: t('iam.users.validation.defaultRoleRequired') }]}
        />
      )}
      <ProFormSwitch
        disabled={isSelf}
        label={t('iam.users.fields.enabled')}
        name="enabled"
      />
      <ProFormTextArea
        fieldProps={{ maxLength: 500, showCount: true }}
        label={t('common.remark')}
        name="remark"
      />
    </ModalForm>
  );
}
