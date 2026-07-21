import { ModalForm, ProFormText, ProFormTextArea } from '@ant-design/pro-components';
import { useTranslation } from 'react-i18next';
import type { Supplier } from '../../api/suppliers';

export interface SupplierFormValues {
  code: string;
  name: string;
  contact?: string;
  address?: string;
  email?: string;
  phone?: string;
  remark?: string;
}

interface SupplierFormModalProps {
  loading: boolean;
  open: boolean;
  supplier?: Supplier;
  onClose: () => void;
  onSubmit: (values: SupplierFormValues) => Promise<boolean>;
}

export function SupplierFormModal({ loading, open, supplier, onClose, onSubmit }: SupplierFormModalProps): JSX.Element {
  const { t } = useTranslation();
  const editing = supplier?.id !== undefined;
  return (
    <ModalForm<SupplierFormValues>
      key={supplier?.id ?? 'new-supplier'}
      initialValues={{
        code: supplier?.code ?? '', name: supplier?.name ?? '', contact: supplier?.contact ?? '',
        address: supplier?.address ?? '', email: supplier?.email ?? '', phone: supplier?.phone ?? '', remark: supplier?.remark ?? '',
      }}
      modalProps={{ destroyOnHidden: true, maskClosable: false }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{ searchConfig: { resetText: t('common.cancel'), submitText: editing ? t('common.save') : t('common.create') }, submitButtonProps: { loading } }}
      title={editing ? t('suppliers.form.editTitle') : t('suppliers.form.createTitle')}
      width={680}
    >
      <ProFormText disabled={editing} fieldProps={{ maxLength: 50 }} label={t('suppliers.fields.code')} name="code" rules={[{ required: true, message: t('suppliers.validation.codeRequired') }]} />
      <ProFormText fieldProps={{ maxLength: 100 }} label={t('suppliers.fields.name')} name="name" rules={[{ required: true, message: t('suppliers.validation.nameRequired') }]} />
      <ProFormText fieldProps={{ maxLength: 100 }} label={t('suppliers.fields.contact')} name="contact" />
      <ProFormText fieldProps={{ maxLength: 30 }} label={t('suppliers.fields.phone')} name="phone" />
      <ProFormText fieldProps={{ maxLength: 100, type: 'email' }} label={t('suppliers.fields.email')} name="email" />
      <ProFormTextArea fieldProps={{ maxLength: 500, showCount: true }} label={t('suppliers.fields.address')} name="address" />
      <ProFormTextArea fieldProps={{ maxLength: 500, showCount: true }} label={t('common.remark')} name="remark" />
    </ModalForm>
  );
}
