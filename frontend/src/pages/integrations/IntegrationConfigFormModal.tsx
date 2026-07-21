import {
  ModalForm,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
} from '@ant-design/pro-components';
import { Alert, Divider } from 'antd';
import { useTranslation } from 'react-i18next';
import type { IntegrationConfig, IntegrationConfigPayload } from '../../api/integrations';

interface IntegrationConfigFormModalProps {
  canManageRetailMode: boolean;
  config?: IntegrationConfig;
  loading: boolean;
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: IntegrationConfigPayload) => Promise<boolean>;
}

export function IntegrationConfigFormModal({
  canManageRetailMode,
  config,
  loading,
  open,
  onClose,
  onSubmit,
}: IntegrationConfigFormModalProps): JSX.Element {
  const { t } = useTranslation();
  const editing = config?.id !== undefined;

  return (
    <ModalForm<IntegrationConfigPayload>
      key={config?.id ?? 'new-integration-config'}
      initialValues={{
        platform: config?.platform ?? 'SHOPIFY',
        storeUrl: config?.storeUrl ?? '',
        clientId: config?.clientId ?? '',
        clientSecret: '',
        accessToken: '',
        isActive: config?.isActive ?? true,
        ...(canManageRetailMode ? { retailMode: config?.retailMode ?? false } : {}),
      }}
      modalProps={{ destroyOnHidden: true, maskClosable: false }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{
        searchConfig: { resetText: t('common.cancel'), submitText: editing ? t('common.save') : t('common.create') },
        submitButtonProps: { loading },
      }}
      title={editing ? t('integrations.config.form.editTitle') : t('integrations.config.form.createTitle')}
      width={680}
    >
      {editing && (
        <Alert
          description={t('integrations.config.form.secretEditHint', {
            secret: config?.clientSecretMasked ?? t('common.notConfigured'),
          })}
          showIcon
          type="info"
        />
      )}
      <Divider orientation="left" plain>{t('integrations.config.form.storeSection')}</Divider>
      <ProFormSelect
        label={t('integrations.config.fields.platform')}
        name="platform"
        options={[{ label: 'Shopify', value: 'SHOPIFY' }]}
        rules={[{ required: true }]}
      />
      <ProFormText
        extra={t('integrations.config.form.storeUrlHint')}
        fieldProps={{ maxLength: 255 }}
        label={t('integrations.config.fields.storeUrl')}
        name="storeUrl"
        placeholder="example.myshopify.com"
        rules={[{ required: true, message: t('integrations.config.validation.storeRequired') }]}
      />
      <Divider orientation="left" plain>{t('integrations.config.form.credentialsSection')}</Divider>
      <ProFormText fieldProps={{ maxLength: 255 }} label={t('integrations.config.fields.clientId')} name="clientId" />
      <ProFormText.Password
        extra={editing ? t('integrations.config.form.blankKeepsSecret') : undefined}
        fieldProps={{ autoComplete: 'new-password', maxLength: 500 }}
        label={t('integrations.config.fields.clientSecret')}
        name="clientSecret"
      />
      <ProFormText.Password
        extra={t('integrations.config.form.legacyTokenHint')}
        fieldProps={{ autoComplete: 'new-password', maxLength: 500 }}
        label={t('integrations.config.fields.accessToken')}
        name="accessToken"
      />
      <ProFormSwitch label={t('integrations.config.fields.active')} name="isActive" />
      {canManageRetailMode && (
        <ProFormSwitch
          extra={t('integrations.config.form.retailModeHint')}
          label={t('integrations.config.fields.retailMode')}
          name="retailMode"
        />
      )}
    </ModalForm>
  );
}
