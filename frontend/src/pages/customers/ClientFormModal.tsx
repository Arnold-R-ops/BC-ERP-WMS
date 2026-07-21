import {
  ModalForm,
  ProFormDigit,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { useTranslation } from 'react-i18next';
import type { Customer, CustomerPayload } from '../../api/masterData';

interface ClientFormModalProps {
  customer?: Customer;
  loading: boolean;
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: CustomerPayload) => Promise<boolean>;
}

export function ClientFormModal({
  customer,
  loading,
  open,
  onClose,
  onSubmit,
}: ClientFormModalProps): JSX.Element {
  const { t } = useTranslation();
  const editing = customer?.id !== undefined;

  return (
    <ModalForm<CustomerPayload>
      key={customer?.id ?? 'new-client'}
      initialValues={{
        code: customer?.code ?? '',
        name: customer?.name ?? '',
        contact: customer?.contact ?? '',
        phone: customer?.phone ?? '',
        email: customer?.email ?? '',
        address: customer?.address ?? '',
        creditLimit: customer?.creditLimit ?? 0,
        isActive: customer?.isActive ?? true,
      }}
      modalProps={{ destroyOnHidden: true, maskClosable: false }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{
        searchConfig: { resetText: t('common.cancel'), submitText: editing ? t('common.save') : t('common.create') },
        submitButtonProps: { loading },
      }}
      title={editing ? t('clients.form.editTitle') : t('clients.form.createTitle')}
      width={680}
    >
      <ProFormText
        disabled={editing}
        fieldProps={{ maxLength: 50 }}
        label={t('clients.fields.code')}
        name="code"
        rules={[{ required: true, message: t('clients.validation.codeRequired') }]}
      />
      <ProFormText
        fieldProps={{ maxLength: 100 }}
        label={t('clients.fields.name')}
        name="name"
        rules={[{ required: true, message: t('clients.validation.nameRequired') }]}
      />
      <ProFormText fieldProps={{ maxLength: 100 }} label={t('clients.fields.contact')} name="contact" />
      <ProFormText fieldProps={{ maxLength: 30 }} label={t('clients.fields.phone')} name="phone" />
      <ProFormText fieldProps={{ maxLength: 100, type: 'email' }} label={t('clients.fields.email')} name="email" />
      <ProFormDigit fieldProps={{ min: 0, precision: 2 }} label={t('clients.fields.creditLimit')} name="creditLimit" />
      <ProFormTextArea fieldProps={{ maxLength: 500, showCount: true }} label={t('clients.fields.address')} name="address" />
      <ProFormSwitch label={t('clients.fields.active')} name="isActive" />
    </ModalForm>
  );
}
